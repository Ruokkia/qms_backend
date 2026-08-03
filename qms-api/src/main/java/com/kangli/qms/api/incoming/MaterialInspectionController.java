package com.kangli.qms.api.incoming;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.service.incoming.dto.MaterialInspectionImportDTO;
import com.kangli.qms.service.incoming.dto.MaterialInspectionImportPreviewVO;
import com.kangli.qms.service.incoming.dto.MaterialInspectionImportResultVO;
import com.kangli.qms.service.incoming.dto.MaterialInspectionReconcileResultVO;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.service.incoming.MaterialInspectionService;
import com.kangli.qms.domain.incoming.vo.KeySupplierTrendVO;
import com.kangli.qms.domain.incoming.vo.MaterialInspectionStatsVO;
import com.kangli.qms.domain.incoming.vo.SupplierRankItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * M1-1/2 物料检验入库 Controller。
 * <p>路径：/api/v1/material-inspections</p>
 * <p>含：CRUD + 看板统计 + 批量导入 + 对账</p>
 */

@Slf4j
@RestController
@RequestMapping("/api/v1/material-inspections")
@Api(tags = "M1-物料检验入库")
public class MaterialInspectionController {

    private final MaterialInspectionService materialInspectionService;

    public MaterialInspectionController(MaterialInspectionService materialInspectionService) {
        this.materialInspectionService = materialInspectionService;
    }

    @GetMapping
    @ApiOperation(value = "分页查询物料检验记录")
    public R<PageResult<MaterialInspection>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String inspectionResult,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LoginUser loginUser = getCurrentLoginUser();
        Page<MaterialInspection> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<MaterialInspection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaterialInspection::getPlantCode, loginUser.getPlantCode().name());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(MaterialInspection::getRecordNo, keyword)
                    .or().like(MaterialInspection::getMaterialBatchNo, keyword)
                    .or().like(MaterialInspection::getMaterialName, keyword)
                    .or().like(MaterialInspection::getMaterialCode, keyword)
                    .or().like(MaterialInspection::getSupplierName, keyword));
        }
        if (StringUtils.hasText(reviewStatus)) {
            wrapper.eq(MaterialInspection::getReviewStatus, reviewStatus);
        }
        if (StringUtils.hasText(inspectionResult)) {
            wrapper.eq(MaterialInspection::getInspectionResult, inspectionResult);
        }
        if (StringUtils.hasText(supplierCode)) {
            wrapper.eq(MaterialInspection::getSupplierCode, supplierCode);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.eq(MaterialInspection::getMaterialCode, materialCode);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(MaterialInspection::getInspectionDate, LocalDate.parse(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(MaterialInspection::getInspectionDate, LocalDate.parse(endDate));
        }
        wrapper.orderByDesc(MaterialInspection::getInspectionDate);
        return R.ok(PageResult.of(materialInspectionService.page(pageObj, wrapper)));
    }

    @GetMapping("/stats")
    @ApiOperation(value = "物料检验看板统计")
    public R<MaterialInspectionStatsVO> stats() {
        return R.ok(materialInspectionService.stats());
    }

    @GetMapping("/key-supplier-trend")
    @ApiOperation(value = "重点供应商质量趋势（自定义时间范围内来料批次量 TopN）")
    public R<KeySupplierTrendVO> keySupplierTrend(
            @RequestParam(defaultValue = "5") int topN,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return R.ok(materialInspectionService.keySupplierTrend(topN, startDate, endDate));
    }

    @GetMapping("/supplier-rank")
    @ApiOperation(value = "供应商合格率排名（可选时间范围）")
    public R<List<SupplierRankItemVO>> supplierRank(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(materialInspectionService.supplierRank(startDate, endDate));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "物料检验详情")
    public R<MaterialInspection> detail(@PathVariable Long id) {
        MaterialInspection record = materialInspectionService.getById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
        }
        return R.ok(record);
    }

    @PostMapping("/import")
    @ApiOperation(value = "批量导入物料检验记录", notes = "默认自动为不合格记录创建异常单")
    public R<MaterialInspectionImportResultVO> importRecords(@RequestBody MaterialInspectionImportDTO dto) {
        return R.ok(materialInspectionService.importRecords(dto));
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation(value = "导入预览", notes = "解析 Excel 并逐行校验（记录编号/检验结果必填、库内唯一），不落库；返回可导入列表与失败明细")
    public R<MaterialInspectionImportPreviewVO> previewImport(@RequestParam("file") MultipartFile file) {
        return R.ok(materialInspectionService.previewImport(file));
    }

    @GetMapping("/import/template")
    @ApiOperation(value = "下载来料检验导入 Excel 模板")
    public void downloadTemplate(HttpServletResponse response) {
        byte[] data = materialInspectionService.generateTemplate();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String fileName = "来料检验导入模板.xlsx";
        String encodedName;
        try {
            encodedName = URLEncoder.encode(fileName, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            encodedName = fileName;
        }
        response.setHeader("Content-Disposition", "attachment; filename=" + encodedName);
        try (OutputStream os = response.getOutputStream()) {
            os.write(data);
            os.flush();
        } catch (IOException ex) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "模板下载失败：" + ex.getMessage());
        }
    }

    @PostMapping("/reconcile")
    @ApiOperation(value = "手动对账（兜底直写库/ETL）", notes = "扫描未关联异常单的不合格记录并自动建单")
    public R<MaterialInspectionReconcileResultVO> reconcile(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String plantCode) {
        return R.ok(materialInspectionService.reconcile(startDate, endDate, plantCode));
    }


    @PutMapping("/{id}")
    @ApiOperation(value = "更新物料检验记录", notes = "当检验结果由\"合格\"变更为\"不合格\"时，会由 Service 在更新事务内自动创建关联异常单（强一致），无需前端额外调用。")
    public R<Void> update(@PathVariable Long id, @RequestBody MaterialInspection record) {
        record.setId(id);
        LoginUser loginUser = getCurrentLoginUser();
        record.setUpdatedBy(loginUser.getRealName());
        materialInspectionService.updateById(record);
        return R.ok(null, "更新成功");
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "逻辑删除物料检验记录")
    public R<Void> delete(@PathVariable Long id) {
        materialInspectionService.removeById(id);
        return R.ok(null, "删除成功");
    }

    private LoginUser getCurrentLoginUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
