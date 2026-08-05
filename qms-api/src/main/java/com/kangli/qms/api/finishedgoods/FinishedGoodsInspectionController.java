package com.kangli.qms.api.finishedgoods;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.service.finishedgoods.FinishedGoodsInspectionService;
import com.kangli.qms.service.finishedgoods.dto.FinishedGoodsInspectionResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * M1-4 成品入库检验 Controller。
 * <p>路径：/api/v1/finished-goods</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/finished-goods")
@Api(tags = "M1-成品入库检验")
public class FinishedGoodsInspectionController {

    private final FinishedGoodsInspectionService finishedGoodsService;

    public FinishedGoodsInspectionController(FinishedGoodsInspectionService finishedGoodsService) {
        this.finishedGoodsService = finishedGoodsService;
    }

    @GetMapping
    @ApiOperation(value = "分页查询成品入库检验")
    public R<PageResult<FinishedGoodsInspectionResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String inspectionResult,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String qcReview,
            @RequestParam(required = false) String mgrApproval,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String dateField,
            @RequestParam(required = false) String endDate) {
        LoginUser loginUser = getCurrentLoginUser();
        Page<FinishedGoodsInspection> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<FinishedGoodsInspection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FinishedGoodsInspection::getPlantCode, loginUser.getPlantCode().name());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(FinishedGoodsInspection::getReportNo, keyword)
                    .or().like(FinishedGoodsInspection::getProductName, keyword)
                    .or().like(FinishedGoodsInspection::getMaterialCode, keyword)
                    .or().like(FinishedGoodsInspection::getProdBatchOrSn, keyword));
        }
        if (StringUtils.hasText(inspectionResult)) {
            wrapper.eq(FinishedGoodsInspection::getInspectionResult, inspectionResult);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(FinishedGoodsInspection::getCategory, category);
        }
        if (StringUtils.hasText(qcReview)) {
            wrapper.eq(FinishedGoodsInspection::getQcReview, qcReview);
        }
        if (StringUtils.hasText(mgrApproval)) {
            wrapper.eq(FinishedGoodsInspection::getMgrApproval, mgrApproval);
        }
        applyDateFilter(wrapper, dateField, startDate, endDate);
        wrapper.orderByDesc(FinishedGoodsInspection::getCreatedAt);
        return R.ok(finishedGoodsService.page(pageObj, wrapper));
    }

    private void applyDateFilter(LambdaQueryWrapper<FinishedGoodsInspection> wrapper, String dateField, String startDate, String endDate) {
        String field = StringUtils.hasText(dateField) ? dateField : "productionDate";
        LocalDate start = StringUtils.hasText(startDate) ? LocalDate.parse(startDate) : null;
        LocalDate end = StringUtils.hasText(endDate) ? LocalDate.parse(endDate) : null;
        if (start == null && end == null) return;
        switch (field) {
            case "expiryDate": applyDate(wrapper, FinishedGoodsInspection::getExpiryDate, start, end); break;
            case "qcReviewTime": applyDateTime(wrapper, FinishedGoodsInspection::getQcReviewTime, start, end); break;
            case "mgrApprovalTime": applyDateTime(wrapper, FinishedGoodsInspection::getMgrApprovalTime, start, end); break;
            case "signatureTime": applyDateTime(wrapper, FinishedGoodsInspection::getSignatureTime, start, end); break;
            case "createdAt": applyDateTime(wrapper, FinishedGoodsInspection::getCreatedAt, start, end); break;
            case "updatedAt": applyDateTime(wrapper, FinishedGoodsInspection::getUpdatedAt, start, end); break;
            default: applyDate(wrapper, FinishedGoodsInspection::getProductionDate, start, end); break;
        }
    }

    private <T> void applyDate(LambdaQueryWrapper<FinishedGoodsInspection> wrapper,
                              com.baomidou.mybatisplus.core.toolkit.support.SFunction<FinishedGoodsInspection, T> column,
                              LocalDate start, LocalDate end) {
        if (start != null) wrapper.ge(column, start);
        if (end != null) wrapper.le(column, end);
    }

    private <T> void applyDateTime(LambdaQueryWrapper<FinishedGoodsInspection> wrapper,
                                  com.baomidou.mybatisplus.core.toolkit.support.SFunction<FinishedGoodsInspection, T> column,
                                  LocalDate start, LocalDate end) {
        if (start != null) wrapper.ge(column, start.atStartOfDay());
        if (end != null) wrapper.le(column, end.atTime(LocalTime.MAX));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "成品检验详情")
    public R<FinishedGoodsInspectionResponse> detail(@PathVariable Long id) {
        return R.ok(finishedGoodsService.detail(id));
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "更新成品入库检验")
    public R<FinishedGoodsInspectionResponse> update(@PathVariable Long id,
                                                     @RequestBody FinishedGoodsInspection record) {
        LoginUser loginUser = getCurrentLoginUser();
        record.setUpdatedBy(loginUser.getRealName());
        return R.ok(finishedGoodsService.update(id, record, loginUser), "更新成功");
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "逻辑删除成品入库检验")
    public R<Void> delete(@PathVariable Long id) {
        finishedGoodsService.delete(id);
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
