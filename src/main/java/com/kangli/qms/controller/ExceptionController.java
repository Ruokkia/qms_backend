package com.kangli.qms.controller;

import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.R;
import com.kangli.qms.dto.EightDSaveDTO;
import com.kangli.qms.dto.ExceptionCloseDTO;
import com.kangli.qms.entity.AuditLog;
import com.kangli.qms.entity.ExceptionOrder;
import com.kangli.qms.service.EightDService;
import com.kangli.qms.service.ExceptionService;
import com.kangli.qms.vo.CloseReadinessVO;
import com.kangli.qms.vo.EightDVO;
import com.kangli.qms.vo.ExceptionAnalysisVO;
import com.kangli.qms.vo.ExceptionDetailVO;
import com.kangli.qms.vo.ExceptionStatsVO;
import com.kangli.qms.vo.QualityRuleCatalogVO;
import com.kangli.qms.vo.SupplierExceptionSummaryVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * M2-2 异常与整改 Controller。
 * <p>路径：/api/v1/exceptions</p>
 * <p>含：CRUD + 详情（含子数组+8D+关联来料）+ 闭环 + KPI 统计 + 多维度分析 + 供应商频次 + 8D 报告</p>
 */


@Slf4j
@RestController
@RequestMapping("/api/v1/exceptions")
@Api(tags = "M2-异常与整改")
public class ExceptionController {

    private final ExceptionService exceptionService;
    private final EightDService eightDService;

    public ExceptionController(ExceptionService exceptionService, EightDService eightDService) {
        this.exceptionService = exceptionService;
        this.eightDService = eightDService;
    }

    @GetMapping
    @ApiOperation(value = "分页查询异常单")
    public R<PageResult<ExceptionOrder>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String processType,
            @RequestParam(required = false) String capaStatus,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(exceptionService.list(page, size, severity, status, supplierId, sourceType,
                processType, capaStatus, startDate, endDate));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "异常单详情（含改善措施+验证记录）")
    public R<ExceptionDetailVO> detail(@PathVariable Long id) {
        return R.ok(exceptionService.detail(id));
    }

    @PostMapping
    @ApiOperation(value = "新增异常单", notes = "自动生成单号 EX-YYYYMMDD-NNN，状态默认待整改")
    public R<ExceptionOrder> create(@RequestBody ExceptionOrder order) {
        return R.ok(exceptionService.create(order), "新增成功");
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "更新异常单")
    public R<Void> update(@PathVariable Long id, @RequestBody ExceptionOrder order) {
        exceptionService.update(id, order);
        return R.ok(null, "更新成功");
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "逻辑删除异常单")
    public R<Void> delete(@PathVariable Long id) {
        exceptionService.delete(id);
        return R.ok(null, "删除成功");
    }

    @PostMapping("/{id}/initiate")
    @ApiOperation(value = "发起整改流程", notes = "选择 CAPA / 8D / BOTH，capaStatus 由待发起→进行中")
    public R<ExceptionOrder> initiate(
            @PathVariable Long id,
            @RequestParam String processType) {
        return R.ok(exceptionService.initiate(id, processType), "发起成功");
    }

    @PostMapping("/{id}/close")
    @ApiOperation(value = "异常闭环", notes = "前置条件：整改计划全部完成 + 改善措施全部DONE + 最新验证通过 + 含8D则D1~D8完整")
    public R<Void> close(@PathVariable Long id, @Valid @RequestBody ExceptionCloseDTO dto) {
        exceptionService.close(id, dto);
        return R.ok(null, "闭环成功");
    }

    @PostMapping("/{id}/reset")
    @ApiOperation(value = "重置异常单为最初状态", notes = "清空 processType/capaStatus/closedAt，status 重置为待整改，逻辑删除关联改善措施/验证/8D/整改计划")
    public R<Void> reset(@PathVariable Long id) {
        exceptionService.reset(id);
        return R.ok(null, "重置成功，异常单已恢复为待整改状态");
    }

    @GetMapping("/{id}/close-readiness")
    @ApiOperation(value = "闭环前置条件检查", notes = "返回逐项检查清单（PASS/FAIL/NA），前端可据此展示闭环按钮是否可点击")
    public R<CloseReadinessVO> closeReadiness(@PathVariable Long id) {
        return R.ok(exceptionService.closeReadiness(id));
    }

    @GetMapping("/by-source/{sourceId}")
    @ApiOperation(value = "根据来源ID查找关联异常单", notes = "如根据 material_inspection.id 查找已关联的 exception_order.id")
    public R<Long> findBySourceId(@PathVariable Long sourceId) {
        Long exceptionId = exceptionService.findExceptionBySourceId(sourceId);
        return R.ok(exceptionId);
    }

    @PostMapping("/from-inspection/{inspectionId}")
    @ApiOperation(value = "从来料检验创建异常整改单", notes = "根据已有的来料检验记录ID创建异常单，不合格记录才可创建")
    public R<ExceptionOrder> createFromInspection(@PathVariable Long inspectionId) {
        return R.ok(exceptionService.createFromInspectionId(inspectionId), "异常单创建成功");
    }

    @GetMapping("/stats")
    @ApiOperation(value = "KPI 看板统计")
    public R<ExceptionStatsVO> stats() {
        return R.ok(exceptionService.stats());
    }

    @GetMapping("/rules/catalog")
    @ApiOperation(value = "查询来料异常判定规则与处理措施", notes = "规则判断数据仅来自来料检验入库审核表")
    public R<QualityRuleCatalogVO> qualityRules() {
        return R.ok(exceptionService.qualityRules());
    }

    @GetMapping("/analysis")
    @ApiOperation(value = "多维度分析", notes = "按 defectDesc/supplier/material/time 聚合异常单次数")
    public R<ExceptionAnalysisVO> analysis(@RequestParam String dimension) {
        return R.ok(exceptionService.analysis(dimension));
    }

    @GetMapping("/{id}/eight-d")
    @ApiOperation(value = "查询 8D 报告")
    public R<EightDVO> getEightD(@PathVariable Long id) {
        return R.ok(eightDService.getByExceptionId(id));
    }

    @PutMapping("/{id}/eight-d")
    @ApiOperation(value = "保存/更新 8D 报告")
    public R<EightDVO> saveEightD(@PathVariable Long id, @Valid @RequestBody EightDSaveDTO dto) {
        return R.ok(eightDService.saveOrUpdate(id, dto));
    }

    @PostMapping("/{id}/eight-d/next-step")
    @ApiOperation(value = "提交 8D 到下一步")
    public R<EightDVO> nextStepEightD(@PathVariable Long id) {
        return R.ok(eightDService.nextStep(id));
    }

    @GetMapping("/supplier-summary")
    @ApiOperation(value = "供应商来料不良频次汇总", notes = "按供应商统计来料不良异常单次数，点击下钻到 /exceptions?supplierId=&sourceType=来料不良")
    public R<List<SupplierExceptionSummaryVO>> supplierSummary(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "1") Integer minCount) {
        return R.ok(exceptionService.supplierSummary(supplierId, startDate, endDate, minCount));
    }

    @GetMapping("/{id}/audit-trail")
    @ApiOperation(value = "审核追溯时间线", notes = "聚合该异常单自身 + 改善措施 + 验证记录 + 8D + 整改计划 的全部审计日志，按操作时间倒序返回")
    public R<List<AuditLog>> auditTrail(@PathVariable Long id) {
        return R.ok(exceptionService.auditTrail(id));
    }
}


