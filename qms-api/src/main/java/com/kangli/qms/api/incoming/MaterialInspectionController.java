package com.kangli.qms.api.incoming;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
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

import java.time.LocalTime;
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
            @RequestParam(required = false) String inspectionResultLike,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String dateField,
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
        if (StringUtils.hasText(inspectionResultLike)) {
            wrapper.like(MaterialInspection::getInspectionResult, inspectionResultLike);
        } else if (StringUtils.hasText(inspectionResult)) {
            if ("__OTHER__".equals(inspectionResult)) {
                wrapper.and(w -> w.notIn(MaterialInspection::getInspectionResult, "合格", "不合格")
                                .or().isNull(MaterialInspection::getInspectionResult));
            } else {
                wrapper.eq(MaterialInspection::getInspectionResult, inspectionResult);
            }
        }
        if (StringUtils.hasText(supplierCode)) {
            wrapper.eq(MaterialInspection::getSupplierCode, supplierCode);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.eq(MaterialInspection::getMaterialCode, materialCode);
        }
        applyDateFilter(wrapper, dateField, startDate, endDate);
        wrapper.orderByDesc(MaterialInspection::getInspectionDate);
        return R.ok(PageResult.of(materialInspectionService.page(pageObj, wrapper)));
    }

    private void applyDateFilter(LambdaQueryWrapper<MaterialInspection> wrapper, String dateField, String startDate, String endDate) {
        String field = StringUtils.hasText(dateField) ? dateField : "inspectionDate";
        LocalDate start = StringUtils.hasText(startDate) ? LocalDate.parse(startDate) : null;
        LocalDate end = StringUtils.hasText(endDate) ? LocalDate.parse(endDate) : null;
        if (start == null && end == null) return;
        switch (field) {
            case "judgementDate": applyDate(wrapper, MaterialInspection::getJudgementDate, start, end); break;
            case "arrivalDate": applyDate(wrapper, MaterialInspection::getArrivalDate, start, end); break;
            case "inspectionEndDate": applyDate(wrapper, MaterialInspection::getInspectionEndDate, start, end); break;
            case "reviewDate": applyDate(wrapper, MaterialInspection::getReviewDate, start, end); break;
            case "submitDate": applyDate(wrapper, MaterialInspection::getSubmitDate, start, end); break;
            case "signatureTime": applyDateTime(wrapper, MaterialInspection::getSignatureTime, start, end); break;
            case "createdAt": applyDateTime(wrapper, MaterialInspection::getCreatedAt, start, end); break;
            case "updatedAt": applyDateTime(wrapper, MaterialInspection::getUpdatedAt, start, end); break;
            default: applyDate(wrapper, MaterialInspection::getInspectionDate, start, end); break;
        }
    }

    private <T> void applyDate(LambdaQueryWrapper<MaterialInspection> wrapper,
                              com.baomidou.mybatisplus.core.toolkit.support.SFunction<MaterialInspection, T> column,
                              LocalDate start, LocalDate end) {
        if (start != null) wrapper.ge(column, start);
        if (end != null) wrapper.le(column, end);
    }

    private <T> void applyDateTime(LambdaQueryWrapper<MaterialInspection> wrapper,
                                  com.baomidou.mybatisplus.core.toolkit.support.SFunction<MaterialInspection, T> column,
                                  LocalDate start, LocalDate end) {
        if (start != null) wrapper.ge(column, start.atStartOfDay());
        if (end != null) wrapper.le(column, end.atTime(LocalTime.MAX));
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

    @GetMapping("/by-barcode")
    @ApiOperation(value = "按物料条码查询检验详情")
    public R<MaterialInspection> getByBarcode(@RequestParam String barcode) {
        return R.ok(materialInspectionService.getByBarcode(barcode));
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
