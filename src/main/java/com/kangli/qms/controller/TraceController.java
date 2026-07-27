package com.kangli.qms.controller;

import com.kangli.qms.common.R;
import com.kangli.qms.service.TraceService;
import com.kangli.qms.vo.TraceDashboardVO;
import com.kangli.qms.vo.TraceNodeDetailVO;
import com.kangli.qms.vo.TraceTreeResultVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * M0 全链路追溯 Controller。
 * <p>提供正向/反向/双向追溯查询、节点详情、看板统计接口。</p>
 * <p>路径：/api/v1/trace/* + /api/v1/trace-nodes/{id}</p>
 */
@Slf4j
@RestController
@Api(tags = "M0 全链路追溯")
public class TraceController {

    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping("/api/v1/trace/forward")
    @ApiOperation(value = "正向追溯（向下：成品→来料）",
            notes = "从起始节点向下递归查找所有子节点，返回树形结构。层级上限 8。")
    public R<TraceTreeResultVO> forward(
            @ApiParam(value = "起始节点编码（SN/物料码/批次号）", required = true)
            @RequestParam String nodeCode,
            @ApiParam(value = "最大层级（默认8，上限8）")
            @RequestParam(required = false) Integer maxLevel) {
        TraceTreeResultVO result = traceService.forwardTrace(nodeCode, maxLevel);
        return R.ok(result);
    }

    @GetMapping("/api/v1/trace/backward")
    @ApiOperation(value = "反向追溯（向上：来料→成品）",
            notes = "从起始节点向上递归查找所有父节点，upward 填充向上链。")
    public R<TraceTreeResultVO> backward(
            @ApiParam(value = "起始节点编码", required = true)
            @RequestParam String nodeCode,
            @ApiParam(value = "最大层级（默认8，上限8）")
            @RequestParam(required = false) Integer maxLevel) {
        TraceTreeResultVO result = traceService.backwardTrace(nodeCode, maxLevel);
        return R.ok(result);
    }

    @GetMapping("/api/v1/trace/full")
    @ApiOperation(value = "双向全链路追溯",
            notes = "正向（children）+ 反向（upward）合并，返回完整追溯链。")
    public R<TraceTreeResultVO> full(
            @ApiParam(value = "起始节点编码", required = true)
            @RequestParam String nodeCode,
            @ApiParam(value = "最大层级（默认8，上限8）")
            @RequestParam(required = false) Integer maxLevel) {
        TraceTreeResultVO result = traceService.fullTrace(nodeCode, maxLevel);
        return R.ok(result);
    }

    @GetMapping("/api/v1/trace-nodes/{id}")
    @ApiOperation(value = "查询追溯节点详情",
            notes = "含节点信息、关联批次信息、父节点、直接子节点列表、IQC 检验明细。")
    public R<TraceNodeDetailVO> nodeDetail(
            @ApiParam(value = "节点ID", required = true)
            @PathVariable Long id) {
        TraceNodeDetailVO detail = traceService.getNodeDetail(id);
        return R.ok(detail);
    }

    @GetMapping("/api/v1/trace/dashboard")
    @ApiOperation(value = "追溯看板统计（KPI）",
            notes = "来料批次总数、合格/异常/在检批次、合格率、PPM、供应商数、SN数、节点总数。")
    public R<TraceDashboardVO> dashboard() {
        TraceDashboardVO dashboard = traceService.getDashboard();
        return R.ok(dashboard);
    }
}
