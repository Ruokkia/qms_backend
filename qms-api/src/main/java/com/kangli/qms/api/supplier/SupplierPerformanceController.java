package com.kangli.qms.api.supplier;

import com.kangli.qms.common.R;
import com.kangli.qms.domain.supplier.vo.SupplierPerfParetoVO;
import com.kangli.qms.domain.supplier.vo.SupplierPerfTrendVO;
import com.kangli.qms.domain.supplier.vo.SupplierPerformanceVO;
import com.kangli.qms.service.supplier.SupplierPerformanceService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 供应商绩效评审接口（挂在 supplier 模块下）。
 * <p>需求 1.1.2：考核指标、自动排名、图表、绩效分级 A/B/C/D。</p>
 */
@RestController
@RequestMapping("/api/v1/suppliers/performance")
@Api(tags = "供应商绩效评审")
public class SupplierPerformanceController {

    @Resource
    private SupplierPerformanceService performanceService;

    @GetMapping("/rank")
    @ApiOperation("供应商绩效排名（多维指标 + 综合评分 + A/B/C/D 等级）")
    public R<List<SupplierPerformanceVO>> rank() {
        return R.ok(performanceService.rank());
    }

    @GetMapping("/trend")
    @ApiOperation("供应商绩效月度趋势（平均合格率/综合分）")
    public R<List<SupplierPerfTrendVO>> trend() {
        return R.ok(performanceService.trend());
    }

    @GetMapping("/pareto")
    @ApiOperation("来料不合格柏拉图（按缺陷类型 + 累计占比）")
    public R<List<SupplierPerfParetoVO>> pareto() {
        return R.ok(performanceService.pareto());
    }
}
