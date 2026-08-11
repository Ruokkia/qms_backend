package com.kangli.qms.service.exception.impl;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.entity.Escalation;
import com.kangli.qms.domain.exception.vo.EscalationCheckResultVO;
import com.kangli.qms.domain.exception.vo.TriggeredSupplierVO;
import com.kangli.qms.domain.supplier.entity.Supplier;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.service.exception.dto.EscalationCheckDTO;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.exception.dto.EscalationCloseDTO;
import com.kangli.qms.service.exception.dto.EscalationCreateDTO;
import com.kangli.qms.service.exception.dto.EscalationExecutionDTO;
import com.kangli.qms.service.exception.dto.EscalationPlanDTO;
import com.kangli.qms.service.exception.dto.EscalationReviewDTO;
import com.kangli.qms.service.exception.dto.EscalationVerificationDTO;
import com.kangli.qms.service.exception.EscalationWorkflowPolicy;
import com.kangli.qms.service.notification.NotificationConfigService;
import com.kangli.qms.service.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EscalationServiceImplTest {

    @AfterEach
    void clearLoginUser() {
        LoginUserHolder.clear();
    }

    @Test
    void checkEscalation_excludesRowsWithoutMappedSupplier() {
        ExceptionOrderMapper exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        NotificationService notificationService = mock(NotificationService.class);
        NotificationConfigService notificationConfigService = mock(NotificationConfigService.class);
        EscalationServiceImpl service = new EscalationServiceImpl(exceptionOrderMapper, supplierMapper, notificationService, notificationConfigService);
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).build());

        TriggeredSupplierVO mapped = candidate(11L, "SUP-SZ-01", "1,2,3");
        TriggeredSupplierVO unmapped = candidate(null, "SUP-SZD-01", "4,5,6");
        when(exceptionOrderMapper.selectRepeatExceptions(eq("SZ"), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(mapped, unmapped));

        EscalationCheckResultVO result = service.checkEscalation(null);

        assertEquals(1, result.getTotalChecked());
        assertEquals("SUP-SZ-01", result.getTriggeredSuppliers().get(0).getSupplierCode());
        assertEquals(Arrays.asList(1L, 2L, 3L), result.getTriggeredSuppliers().get(0).getRelatedExceptionIds());
    }

    // ===================== M2-043 自动升级触发分支（单测 P0） =====================

    /** 达到重复阈值且已映射供应商：标记为应升级，重复次数与窗口天数透传。 */
    @Test
    void m2_043_repeatAtThreshold_isFlaggedForEscalation() {
        ExceptionOrderMapper exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        EscalationServiceImpl service = new EscalationServiceImpl(exceptionOrderMapper, mock(SupplierMapper.class),
                mock(NotificationService.class), mock(NotificationConfigService.class));
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).build());

        TriggeredSupplierVO hit = candidate(11L, "SUP-SZ-01", "1,2,3");
        when(exceptionOrderMapper.selectRepeatExceptions(eq("SZ"), eq(90), eq(3)))
                .thenReturn(Collections.singletonList(hit));

        EscalationCheckResultVO result = service.checkEscalation(null);

        assertEquals(1, result.getTotalChecked());
        TriggeredSupplierVO flagged = result.getTriggeredSuppliers().get(0);
        assertTrue(flagged.getShouldEscalate(), "达阈值应标记 shouldEscalate=true");
        assertEquals(3, flagged.getRepeatCount());
        assertEquals(90, flagged.getWindowDays());
    }

    /** 自定义窗口与阈值正确下推到 mapper（而非硬编码 90/3）。 */
    @Test
    void m2_043_customWindowAndThreshold_pushedToMapper() {
        ExceptionOrderMapper exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        EscalationServiceImpl service = new EscalationServiceImpl(exceptionOrderMapper, mock(SupplierMapper.class),
                mock(NotificationService.class), mock(NotificationConfigService.class));
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).build());

        EscalationCheckDTO dto = new EscalationCheckDTO();
        dto.setDaysWindow(60);
        dto.setMinRepeatCount(5);

        when(exceptionOrderMapper.selectRepeatExceptions(eq("SZ"), eq(60), eq(5)))
                .thenReturn(Collections.emptyList());

        service.checkEscalation(dto);

        // 验证参数确为 60/5，而非默认的 90/3
        verify(exceptionOrderMapper).selectRepeatExceptions(eq("SZ"), eq(60), eq(5));
    }

    /** 指定 supplierId 时只保留该供应商候选。 */
    @Test
    void m2_043_supplierIdFilter_keepsOnlyMatched() {
        ExceptionOrderMapper exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        EscalationServiceImpl service = new EscalationServiceImpl(exceptionOrderMapper, mock(SupplierMapper.class),
                mock(NotificationService.class), mock(NotificationConfigService.class));
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).build());

        TriggeredSupplierVO a = candidate(11L, "SUP-SZ-01", "1,2,3");
        TriggeredSupplierVO b = candidate(22L, "SUP-SZ-02", "4,5,6");
        when(exceptionOrderMapper.selectRepeatExceptions(eq("SZ"), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(a, b));

        EscalationCheckDTO dto = new EscalationCheckDTO();
        dto.setSupplierId(22L);

        EscalationCheckResultVO result = service.checkEscalation(dto);

        assertEquals(1, result.getTotalChecked());
        assertEquals("SUP-SZ-02", result.getTriggeredSuppliers().get(0).getSupplierCode());
    }

    /** 无触发候选：totalChecked=0，triggeredSuppliers 为空。 */
    @Test
    void m2_043_noCandidate_totalCheckedZero() {
        ExceptionOrderMapper exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        EscalationServiceImpl service = new EscalationServiceImpl(exceptionOrderMapper, mock(SupplierMapper.class),
                mock(NotificationService.class), mock(NotificationConfigService.class));
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).build());

        when(exceptionOrderMapper.selectRepeatExceptions(eq("SZ"), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        EscalationCheckResultVO result = service.checkEscalation(null);

        assertEquals(0, result.getTotalChecked());
        assertTrue(result.getTriggeredSuppliers().isEmpty());
    }

    @Test
    void create_rejectsUnknownSupplierBeforePersistingEscalation() {
        ExceptionOrderMapper exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        EscalationServiceImpl service = new EscalationServiceImpl(exceptionOrderMapper, supplierMapper,
                mock(NotificationService.class), mock(NotificationConfigService.class));
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).realName("测试用户").build());
        when(supplierMapper.selectById(999L)).thenReturn(null);

        EscalationCreateDTO dto = new EscalationCreateDTO();
        dto.setSupplierId(999L);
        dto.setEscalationReason("重复来料异常");
        dto.setEscalationAction("加密审核");

        assertThrows(RuntimeException.class, () -> service.create(dto));
    }

    @Test
    void create_usesSupplierMasterAndPendingReviewStatus() {
        ExceptionOrderMapper exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        EscalationServiceImpl service = spy(new EscalationServiceImpl(exceptionOrderMapper, supplierMapper,
                mock(NotificationService.class), mock(NotificationConfigService.class)));
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).realName("测试用户").build());
        Supplier supplier = new Supplier();
        supplier.setId(11L);
        supplier.setSupplierCode("SUP-SZ-01");
        supplier.setSupplierName("深圳电子元件有限公司");
        supplier.setPlantCode("SZ");
        supplier.setStatus("启用");
        when(supplierMapper.selectById(11L)).thenReturn(supplier);
        doReturn(true).when(service).save(any(Escalation.class));

        EscalationCreateDTO dto = new EscalationCreateDTO();
        dto.setSupplierId(11L);
        dto.setEscalationReason("重复来料异常");
        dto.setEscalationAction("加密审核");
        dto.setMaterialCode("MC-001");

        Escalation created = service.create(dto);

        assertEquals("11", created.getSupplierId());
        assertEquals("SUP-SZ-01", created.getSupplierCode());
        assertEquals("MC-001", created.getMaterialCode());
        assertEquals("PENDING_REVIEW", created.getStatus());
        assertEquals("PENDING_REVIEW", created.getProcessStage());
        verify(service).save(created);
    }

    @Test
    void savePlan_bindsCurrentLoginUserAsPlanWriter() {
        ExceptionOrderMapper exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        SupplierMapper supplierMapper = mock(SupplierMapper.class);
        EscalationServiceImpl service = spy(new EscalationServiceImpl(exceptionOrderMapper, supplierMapper,
                mock(NotificationService.class), mock(NotificationConfigService.class)));
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).realName("测试用户").build());
        Escalation escalation = new Escalation();
        escalation.setId(1L);
        escalation.setPlantCode("SZ");
        escalation.setProcessStage("PLAN");
        doReturn(escalation).when(service).getById(1L);
        doReturn(true).when(service).updateById(any(Escalation.class));
        EscalationPlanDTO dto = new EscalationPlanDTO();
        dto.setActionPlan("增加来料抽检频次");
        dto.setOwnerName("供应商质量工程师");

        Escalation result = service.savePlan(1L, dto);

        assertEquals("测试用户", result.getPlanFilledBy());
        verify(service).updateById(result);
    }

    // ===================== M2-045 供应商升级状态机全流程（单测 P0） =====================

    /** 审核 APPROVE：PENDING_REVIEW -> PLAN，status=ACTIVE。 */
    @Test
    void m2_045_reviewApprove_advancesToPlan() {
        EscalationServiceImpl service = spyService();
        Escalation escalation = pendingReviewEscalation();
        doReturn(escalation).when(service).getById(1L);
        doReturn(true).when(service).updateById(any(Escalation.class));

        EscalationReviewDTO dto = new EscalationReviewDTO();
        dto.setDecision("APPROVE");
        dto.setOpinion("同意升级");

        Escalation result = service.review(1L, dto);

        assertEquals(EscalationWorkflowPolicy.PLAN, result.getProcessStage());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("同意升级", result.getReviewOpinion());
    }

    /** 审核 REJECT：PENDING_REVIEW -> REJECTED。 */
    @Test
    void m2_045_reviewReject_toRejected() {
        EscalationServiceImpl service = spyService();
        Escalation escalation = pendingReviewEscalation();
        doReturn(escalation).when(service).getById(1L);
        doReturn(true).when(service).updateById(any(Escalation.class));

        EscalationReviewDTO dto = new EscalationReviewDTO();
        dto.setDecision("REJECT");
        dto.setOpinion("证据不足");

        Escalation result = service.review(1L, dto);

        assertEquals(EscalationWorkflowPolicy.REJECTED, result.getProcessStage());
        assertEquals("REJECTED", result.getStatus());
    }

    /** 阶段守卫：非 PENDING_REVIEW 时 review 抛 400。 */
    @Test
    void m2_045_reviewOnWrongStage_throws400() {
        EscalationServiceImpl service = spyService();
        Escalation escalation = new Escalation();
        escalation.setId(1L);
        escalation.setPlantCode("SZ");
        escalation.setProcessStage(EscalationWorkflowPolicy.EXECUTION);
        doReturn(escalation).when(service).getById(1L);

        EscalationReviewDTO dto = new EscalationReviewDTO();
        dto.setDecision("APPROVE");
        dto.setOpinion("x");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.review(1L, dto));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /** 提交执行跟踪：EXECUTION -> VERIFICATION。 */
    @Test
    void m2_045_submitExecution_toVerification() {
        EscalationServiceImpl service = spyService();
        Escalation escalation = new Escalation();
        escalation.setId(1L);
        escalation.setPlantCode("SZ");
        escalation.setProcessStage(EscalationWorkflowPolicy.EXECUTION);
        doReturn(escalation).when(service).getById(1L);
        doReturn(true).when(service).updateById(any(Escalation.class));

        EscalationExecutionDTO dto = new EscalationExecutionDTO();
        dto.setExecutionRecord("已暂停供货并加密审核");

        Escalation result = service.submitExecution(1L, dto);
        assertEquals(EscalationWorkflowPolicy.VERIFICATION, result.getProcessStage());
        assertEquals("已暂停供货并加密审核", result.getExecutionRecord());
    }

    /** 效果验证 PASS：VERIFICATION -> PENDING_CLOSE_APPROVAL。 */
    @Test
    void m2_045_verifyPass_toPendingCloseApproval() {
        EscalationServiceImpl service = spyService();
        Escalation escalation = new Escalation();
        escalation.setId(1L);
        escalation.setPlantCode("SZ");
        escalation.setProcessStage(EscalationWorkflowPolicy.VERIFICATION);
        doReturn(escalation).when(service).getById(1L);
        doReturn(true).when(service).updateById(any(Escalation.class));

        EscalationVerificationDTO dto = new EscalationVerificationDTO();
        dto.setResult("PASS");
        dto.setEvidence("不良率降至 0.1%");

        Escalation result = service.verify(1L, dto);
        assertEquals(EscalationWorkflowPolicy.PENDING_CLOSE_APPROVAL, result.getProcessStage());
        assertEquals("PASS", result.getVerificationResult());
    }

    /** 效果验证 FAIL：VERIFICATION -> 回退 PLAN。 */
    @Test
    void m2_045_verifyFail_rollsBackToPlan() {
        EscalationServiceImpl service = spyService();
        Escalation escalation = new Escalation();
        escalation.setId(1L);
        escalation.setPlantCode("SZ");
        escalation.setProcessStage(EscalationWorkflowPolicy.VERIFICATION);
        doReturn(escalation).when(service).getById(1L);
        doReturn(true).when(service).updateById(any(Escalation.class));

        EscalationVerificationDTO dto = new EscalationVerificationDTO();
        dto.setResult("FAIL");
        dto.setEvidence("不良复现");

        Escalation result = service.verify(1L, dto);
        assertEquals(EscalationWorkflowPolicy.PLAN, result.getProcessStage());
        assertEquals("FAIL", result.getVerificationResult());
    }

    /** 验证结论非法（非 PASS/FAIL）抛 400。 */
    @Test
    void m2_045_verifyInvalidResult_throws400() {
        EscalationServiceImpl service = spyService();
        Escalation escalation = new Escalation();
        escalation.setId(1L);
        escalation.setPlantCode("SZ");
        escalation.setProcessStage(EscalationWorkflowPolicy.VERIFICATION);
        doReturn(escalation).when(service).getById(1L);

        EscalationVerificationDTO dto = new EscalationVerificationDTO();
        dto.setResult("PARTIAL");
        dto.setEvidence("x");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.verify(1L, dto));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /** 关闭审批：PENDING_CLOSE_APPROVAL -> CLOSED。 */
    @Test
    void m2_045_close_toClosed() {
        EscalationServiceImpl service = spyService();
        Escalation escalation = new Escalation();
        escalation.setId(1L);
        escalation.setPlantCode("SZ");
        escalation.setProcessStage(EscalationWorkflowPolicy.PENDING_CLOSE_APPROVAL);
        doReturn(escalation).when(service).getById(1L);
        doReturn(true).when(service).updateById(any(Escalation.class));

        EscalationCloseDTO dto = new EscalationCloseDTO();
        dto.setReason("措施有效，准予关闭");

        Escalation result = service.close(1L, dto);
        assertEquals(EscalationWorkflowPolicy.CLOSED, result.getProcessStage());
        assertEquals("CLOSED", result.getStatus());
        assertEquals("措施有效，准予关闭", result.getCloseReason());
    }

    /** M2-045 端到端：APPROVE -> PLAN -> EXECUTION -> VERIFICATION -> PASS -> PENDING_CLOSE_APPROVAL -> CLOSED。 */
    @Test
    void m2_045_endToEnd_happyPath() {
        EscalationServiceImpl service = spyService();
        Escalation escalation = pendingReviewEscalation();
        doReturn(escalation).when(service).getById(1L);
        doReturn(true).when(service).updateById(any(Escalation.class));

        EscalationReviewDTO review = new EscalationReviewDTO();
        review.setDecision("APPROVE");
        review.setOpinion("同意");
        assertEquals(EscalationWorkflowPolicy.PLAN, service.review(1L, review).getProcessStage());

        EscalationPlanDTO plan = new EscalationPlanDTO();
        plan.setActionPlan("加密审核");
        plan.setOwnerName("SQE");
        assertEquals(EscalationWorkflowPolicy.EXECUTION, service.savePlan(1L, plan).getProcessStage());

        EscalationExecutionDTO exec = new EscalationExecutionDTO();
        exec.setExecutionRecord("已执行");
        assertEquals(EscalationWorkflowPolicy.VERIFICATION, service.submitExecution(1L, exec).getProcessStage());

        EscalationVerificationDTO verify = new EscalationVerificationDTO();
        verify.setResult("PASS");
        verify.setEvidence("有效");
        assertEquals(EscalationWorkflowPolicy.PENDING_CLOSE_APPROVAL, service.verify(1L, verify).getProcessStage());

        EscalationCloseDTO close = new EscalationCloseDTO();
        close.setReason("关闭");
        Escalation closed = service.close(1L, close);
        assertEquals(EscalationWorkflowPolicy.CLOSED, closed.getProcessStage());
        assertEquals("CLOSED", closed.getStatus());
    }

    private EscalationServiceImpl spyService() {
        EscalationServiceImpl service = spy(new EscalationServiceImpl(
                mock(ExceptionOrderMapper.class), mock(SupplierMapper.class),
                mock(NotificationService.class), mock(NotificationConfigService.class)));
        LoginUserHolder.set(LoginUser.builder().plantCode(PlantCode.SZ).realName("测试用户").build());
        return service;
    }

    private Escalation pendingReviewEscalation() {
        Escalation escalation = new Escalation();
        escalation.setId(1L);
        escalation.setPlantCode("SZ");
        escalation.setStatus(EscalationWorkflowPolicy.PENDING_REVIEW);
        escalation.setProcessStage(EscalationWorkflowPolicy.PENDING_REVIEW);
        return escalation;
    }

    private TriggeredSupplierVO candidate(Long supplierId, String supplierCode, String exceptionIds) {
        TriggeredSupplierVO candidate = new TriggeredSupplierVO();
        candidate.setSupplierId(supplierId);
        candidate.setSupplierCode(supplierCode);
        candidate.setMaterialCode("MAT-001");
        candidate.setRepeatCount(3);
        candidate.setRelatedExceptionIdsStr(exceptionIds);
        return candidate;
    }
}
