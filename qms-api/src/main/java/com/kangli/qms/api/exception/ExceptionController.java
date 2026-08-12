package com.kangli.qms.api.exception;

import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.R;
import com.kangli.qms.service.exception.dto.CapaPhaseApprovalDTO;
import com.kangli.qms.service.exception.dto.EightDSaveDTO;
import com.kangli.qms.service.exception.dto.ExceptionCloseDTO;
import com.kangli.qms.service.exception.dto.ExceptionInitiateDTO;
import com.kangli.qms.service.exception.dto.StageApprovalDTO;
import com.kangli.qms.service.exception.dto.ExceptionUpdateDTO;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.service.exception.EightDService;
import com.kangli.qms.service.exception.ExceptionService;
import com.kangli.qms.service.exception.dto.EightDD1TeamDTO;
import com.kangli.qms.service.exception.dto.EightDD1ReviewDTO;
import com.kangli.qms.domain.exception.vo.CapaPhaseApprovalReadinessVO;
import com.kangli.qms.domain.exception.vo.CloseReadinessVO;
import com.kangli.qms.domain.exception.vo.EightDStepLogVO;
import com.kangli.qms.domain.exception.vo.EightDVO;
import com.kangli.qms.domain.exception.vo.ExceptionAnalysisVO;
import com.kangli.qms.domain.exception.vo.ExceptionDetailVO;
import com.kangli.qms.domain.exception.vo.ExceptionSourceOptionVO;
import com.kangli.qms.domain.exception.vo.ExceptionStatsVO;
import com.kangli.qms.domain.exception.vo.ExceptionUserOptionVO;
import com.kangli.qms.domain.exception.vo.QualityRuleCatalogVO;
import com.kangli.qms.domain.supplier.vo.SupplierExceptionSummaryVO;
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

    @GetMapping("/{id:\\d+}")
    @ApiOperation(value = "异常单详情（含改善措施+验证记录）")
    public R<ExceptionDetailVO> detail(@PathVariable Long id) {
        return R.ok(exceptionService.detail(id));
    }

    @PostMapping
    @ApiOperation(value = "新增异常单", notes = "自动生成单号 EX-YYYYMMDD-NNN，状态默认待整改")
    public R<ExceptionOrder> create(@RequestBody ExceptionOrder order) {
        return R.ok(exceptionService.create(order), "新增成功");
    }

    @PutMapping("/{id:\\d+}")
    @ApiOperation(value = "更新异常单", notes = "仅接受白名单字段，plantCode/exceptionNo/createdBy 等系统字段不可被篡改")
    public R<Void> update(@PathVariable Long id, @RequestBody ExceptionUpdateDTO dto) {
        exceptionService.update(id, dto);
        return R.ok(null, "更新成功");
    }

    @DeleteMapping("/{id:\\d+}")
    @ApiOperation(value = "逻辑删除异常单")
    public R<Void> delete(@PathVariable Long id) {
        exceptionService.delete(id);
        return R.ok(null, "删除成功");
    }

    @PostMapping("/{id:\\d+}/initiate")
    @ApiOperation(value = "发起整改流程（D0 立案 + 指派）",
            notes = "质量部门手动选择 CAPA / 8D / BOTH；同时填写 D0 发起说明并指派 8D 团队与 CAPA 负责人。" +
                    "系统不预填推荐值。capaStatus 由待发起→进行中。BOTH 模式下 capaPhase 初始化为 INITIATE。")
    public R<ExceptionOrder> initiate(
            @PathVariable Long id,
            @Valid @RequestBody ExceptionInitiateDTO dto) {
        return R.ok(exceptionService.initiate(id, dto), "发起成功");
    }

    @PostMapping("/{id:\\d+}/capa/approve-root-cause")
    @ApiOperation(value = "CAPA 根因审批（BOTH 模式）",
            notes = "8D 完成 D4 根因分析后，CAPA 质量部门审批根因分析结果。审批通过后 capaPhase→ROOT_CAUSE_APPROVED，8D 方可推进至 D5。")
    public R<Void> approveCapaRootCause(
            @PathVariable Long id,
            @Valid @RequestBody CapaPhaseApprovalDTO dto) {
        exceptionService.approveCapaRootCause(id, dto.getComment());
        return R.ok(null, "根因审批通过，8D 可推进至 D5");
    }

    @PostMapping("/{id:\\d+}/capa/approve-measures")
    @ApiOperation(value = "CAPA 措施审批（BOTH 模式）",
            notes = "8D 完成 D5 措施方案后，CAPA 质量部门审批措施方案。审批通过后 capaPhase→MEASURES_APPROVED，8D 方可推进至 D6。")
    public R<Void> approveCapaMeasures(
            @PathVariable Long id,
            @Valid @RequestBody CapaPhaseApprovalDTO dto) {
        exceptionService.approveCapaMeasures(id, dto.getComment());
        return R.ok(null, "措施审批通过，8D 可推进至 D6");
    }

    @GetMapping("/user-options")
    @ApiOperation(value = "人员选项列表",
            notes = "用于「发起整改」时选择整改责任人、组建 8D 团队、指派 CAPA 负责人。仅返回 id/姓名/角色/分公司，走 M2 异常模块权限。")
    public R<List<ExceptionUserOptionVO>> userOptions() {
        return R.ok(exceptionService.listUserOptions());
    }

    @PostMapping("/{id:\\d+}/close")
    @ApiOperation(value = "异常闭环", notes = "前置条件：整改计划全部完成 + 改善措施全部DONE + 最新验证通过 + 含8D则D1~D8完整")
    public R<Void> close(@PathVariable Long id, @Valid @RequestBody ExceptionCloseDTO dto) {
        exceptionService.close(id, dto);
        return R.ok(null, "闭环成功");
    }

    @PostMapping("/{id:\\d+}/reset")
    @ApiOperation(value = "重置异常单为最初状态", notes = "清空 processType/capaStatus/closedAt，status 重置为待整改，逻辑删除关联改善措施/验证/8D/整改计划")
    public R<Void> reset(@PathVariable Long id) {
        exceptionService.reset(id);
        return R.ok(null, "重置成功，异常单已恢复为待整改状态");
    }

    @GetMapping("/{id:\\d+}/close-readiness")
    @ApiOperation(value = "闭环前置条件检查", notes = "返回逐项检查清单（PASS/FAIL/NA），前端可据此展示闭环按钮是否可点击")
    public R<CloseReadinessVO> closeReadiness(@PathVariable Long id) {
        return R.ok(exceptionService.closeReadiness(id));
    }

    @GetMapping("/{id:\\d+}/capa-phase-readiness")
    @ApiOperation(value = "CAPA 相位审批就绪检查", notes = "BOTH模式：返回当前相位及当前用户是否有审批权限")
    public R<CapaPhaseApprovalReadinessVO> capaPhaseApprovalReadiness(@ApiParam("异常单ID") @PathVariable Long id) {
        return R.ok(exceptionService.capaPhaseApprovalReadiness(id));
    }

    @GetMapping("/by-source/{sourceId}")
    @ApiOperation(value = "根据来源ID查找关联异常单", notes = "如根据 material_inspection.id 查找已关联的 exception_order.id")
    public R<Long> findBySourceId(@PathVariable Long sourceId) {
        Long exceptionId = exceptionService.findExceptionBySourceId(sourceId);
        return R.ok(exceptionId);
    }

    @GetMapping("/source-options")
    @ApiOperation(value = "异常单「选择源头记录」聚合查询",
            notes = "按来源类型从不同源头库查询并统一返回精简字段（id/物料编码/物料名称/批号/供应商/工单号），供新建异常单表单选择。" +
                    "sourceType=material 查来料库、fai 查首件库、finished 查成品库；complaint/process/other 模糊搜索来料+成品库。")
    public R<PageResult<ExceptionSourceOptionVO>> sourceOptions(
            @ApiParam(value = "来源类型：material/fai/finished/complaint/process/other", required = true)
            @RequestParam String sourceType,
            @ApiParam(value = "模糊关键词（物料编码/名称/批号/供应商），可选")
            @RequestParam(required = false) String keyword,
            @ApiParam(value = "页码，从1开始") @RequestParam(defaultValue = "1") int page,
            @ApiParam(value = "每页条数，上限100") @RequestParam(defaultValue = "20") int size) {
        return R.ok(exceptionService.listSourceOptions(sourceType, keyword, page, size));
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

    @GetMapping("/{id:\\d+}/eight-d")
    @ApiOperation(value = "查询 8D 报告")
    public R<EightDVO> getEightD(@PathVariable Long id) {
        return R.ok(eightDService.getByExceptionId(id));
    }

    @PutMapping("/{id:\\d+}/eight-d")
    @ApiOperation(value = "保存/更新 8D 报告")
    public R<EightDVO> saveEightD(@PathVariable Long id, @Valid @RequestBody EightDSaveDTO dto) {
        return R.ok(eightDService.saveOrUpdate(id, dto));
    }

    @PostMapping("/{id:\\d+}/eight-d/next-step")
    @ApiOperation(value = "提交 8D/CAPA 到下一步（阶段级审批拦截）",
            notes = "推进到目标阶段；若该阶段配置了审批，则进入 PENDING_APPROVAL 等待审批，直至 approveStage 通过。")
    public R<EightDVO> nextStepEightD(@PathVariable Long id) {
        return R.ok(eightDService.nextStep(id));
    }

    @PostMapping("/{id:\\d+}/eight-d/approve")
    @ApiOperation(value = "阶段审批通过", notes = "审批人为配置角色（R04/R06）。通过后可继续推进到下一步。")
    public R<EightDVO> approveStage(
            @PathVariable Long id,
            @Valid @RequestBody StageApprovalDTO dto) {
        return R.ok(eightDService.approveStage(id, dto));
    }

    @PostMapping("/{id:\\d+}/eight-d/reject")
    @ApiOperation(value = "阶段审批驳回", notes = "唯一允许的回退例外：驳回后回退到上一阶段重新填写，直到通过为止。")
    public R<EightDVO> rejectStage(
            @PathVariable Long id,
            @Valid @RequestBody StageApprovalDTO dto) {
        return R.ok(eightDService.rejectStage(id, dto));
    }

    @GetMapping("/{id:\\d+}/eight-d/history")
    @ApiOperation(value = "查询 8D 步骤留痕", notes = "返回该异常单 8D 报告每一步的保存/推进操作快照，按操作时间升序，支撑审计追溯")
    public R<List<EightDStepLogVO>> eightDHistory(@PathVariable Long id) {
        return R.ok(eightDService.getStepLogs(id));
    }

    @PostMapping("/{id:\\d+}/eight-d/d1/team/submit")
    @ApiOperation(value = "D1 团队提交", notes = "指定负责人自行组建团队后提交质量部审核。仅当前步骤为 D1 且用户为指定负责人时允许。")
    public R<EightDVO> submitD1Team(
            @PathVariable Long id,
            @Valid @RequestBody EightDD1TeamDTO dto) {
        return R.ok(eightDService.submitD1Team(id, dto));
    }

    @PostMapping("/{id:\\d+}/eight-d/d1/team/review")
    @ApiOperation(value = "D1 团队审核", notes = "质量部门审核负责人提交的 D1 团队。通过则推进至 D2，驳回则退回 D1 重新组建。仅质量角色 R04/R06 允许。")
    public R<EightDVO> reviewD1Team(
            @PathVariable Long id,
            @Valid @RequestBody EightDD1ReviewDTO dto) {
        return R.ok(eightDService.reviewD1Team(id, dto));
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

    @GetMapping("/{id:\\d+}/audit-trail")
    @ApiOperation(value = "审核追溯时间线", notes = "聚合该异常单自身 + 改善措施 + 验证记录 + 8D + 整改计划 的全部审计日志，按操作时间倒序返回")
    public R<List<AuditLog>> auditTrail(@PathVariable Long id) {
        return R.ok(exceptionService.auditTrail(id));
    }
}


