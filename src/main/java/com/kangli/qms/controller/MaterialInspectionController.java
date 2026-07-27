package com.kangli.qms.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.dto.MaterialInspectionImportDTO;
import com.kangli.qms.dto.MaterialInspectionImportResultVO;
import com.kangli.qms.dto.MaterialInspectionReconcileResultVO;
import com.kangli.qms.entity.MaterialInspection;
import com.kangli.qms.service.MaterialInspectionService;
import com.kangli.qms.vo.KeySupplierTrendVO;
import com.kangli.qms.vo.MaterialInspectionStatsVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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
    @ApiOperation(value = "重点供应商质量趋势（近30天来料批次量 Top5）")
    public R<KeySupplierTrendVO> keySupplierTrend() {
        return R.ok(materialInspectionService.keySupplierTrend());
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

    @PostMapping
    @ApiOperation(value = "新增物料检验记录", notes = "inspectionResult=不合格 且 autoCreateException=true 时自动创建异常单")
    public R<MaterialInspection> create(@RequestBody MaterialInspection record,
                                        @RequestParam(required = false, defaultValue = "true") boolean autoCreateException) {
        return R.ok(materialInspectionService.saveWithException(record, autoCreateException), "新增成功");
    }

    @PostMapping("/import")
    @ApiOperation(value = "批量导入物料检验记录", notes = "默认自动为不合格记录创建异常单")
    public R<MaterialInspectionImportResultVO> importRecords(@RequestBody MaterialInspectionImportDTO dto) {
        return R.ok(materialInspectionService.importRecords(dto));
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
    @ApiOperation(value = "更新物料检验记录")
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
