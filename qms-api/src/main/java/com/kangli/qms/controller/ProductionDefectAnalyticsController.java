package com.kangli.qms.controller;

import com.kangli.qms.common.*;
import com.kangli.qms.dto.DefectAnalyticsQuery;
import com.kangli.qms.service.ProductionDefectAnalyticsService;
import com.kangli.qms.vo.DefectAnalyticsSummaryVO;
import com.kangli.qms.vo.DefectRankItemVO;
import com.kangli.qms.vo.DefectTrendPointVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/production-defect-analytics")
@Api(tags = "M1-生产不良分析（精简）")
@RequiredArgsConstructor
public class ProductionDefectAnalyticsController {

    private final ProductionDefectAnalyticsService service;

    @GetMapping("/summary")
    @ApiOperation("概览：本期指标值/环比%/同比%/报废数量/报废率/高频工序TOP1")
    public R<DefectAnalyticsSummaryVO> summary(@Valid DefectAnalyticsQuery q) {
        return R.ok(service.summary(q, currentUser().getPlantCode().name()));
    }

    @GetMapping("/trend")
    @ApiOperation("趋势：按粒度分组，每点带环比/同比 delta")
    public R<List<DefectTrendPointVO>> trend(@Valid DefectAnalyticsQuery q) {
        return R.ok(service.trend(q, currentUser().getPlantCode().name()));
    }

    @GetMapping("/rank")
    @ApiOperation("高频排名：按工序/不良代码 Top-N")
    public R<List<DefectRankItemVO>> rank(@Valid DefectAnalyticsQuery q) {
        return R.ok(service.rank(q, currentUser().getPlantCode().name()));
    }

    private LoginUser currentUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
