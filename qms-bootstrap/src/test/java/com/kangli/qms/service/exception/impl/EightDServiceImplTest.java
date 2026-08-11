package com.kangli.qms.service.exception.impl;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangli.qms.service.exception.dto.EightDSaveDTO;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.exception.entity.Exception8d;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.mapper.Exception8dMapper;
import com.kangli.qms.domain.exception.mapper.Exception8dStepLogMapper;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.vo.EightDVO;
import com.kangli.qms.service.exception.ExceptionApprovalConfigService;
import com.kangli.qms.service.notification.NotificationConfigService;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.enums.PlantCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

/**
 * 8D 报告「只进不退、逐步推进」约束的回归测试。
 * <p>对应安全用例 M2-024：PUT /eight-d 曾可直接回退/跳步绕过 nextStep，
 * 现由 saveOrUpdate 统一拦截（EIGHT_D_STEP_INVALID=2006 / EIGHT_D_STEP_JUMP=2009）。</p>
 */
class EightDServiceImplTest {

    private final Exception8dMapper eightdMapper = mock(Exception8dMapper.class);
    private final ExceptionOrderMapper orderMapper = mock(ExceptionOrderMapper.class);
    private final Exception8dStepLogMapper stepLogMapper = mock(Exception8dStepLogMapper.class);
    private final ExceptionApprovalConfigService approvalConfigService = mock(ExceptionApprovalConfigService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final SysUserMapper sysUserMapper = mock(SysUserMapper.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final NotificationConfigService notificationConfigService = mock(NotificationConfigService.class);
    private final EightDServiceImpl service = new EightDServiceImpl(
            eightdMapper, orderMapper, stepLogMapper,
            approvalConfigService, notificationService,
            notificationConfigService, sysUserMapper, objectMapper);

    private static final long EXCEPTION_ID = 1L;

    @BeforeEach
    void setUp() {
        LoginUserHolder.set(sampleUser());
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private void stubOrder() {
        ExceptionOrder order = new ExceptionOrder();
        order.setId(EXCEPTION_ID);
        order.setPlantCode("P1");
        order.setPlantName("工厂一");
        order.setStatus("处理中");
        when(orderMapper.selectById(EXCEPTION_ID)).thenReturn(order);
    }

    private static Exception8d existingAt(String step) {
        Exception8d existing = new Exception8d();
        existing.setId(100L);
        existing.setExceptionId(EXCEPTION_ID);
        existing.setCurrentStep(step);
        existing.setIsDeleted((short) 0);
        return existing;
    }

    private static EightDSaveDTO dtoWithStep(String step) {
        EightDSaveDTO dto = new EightDSaveDTO();
        dto.setCurrentStep(step);
        return dto;
    }

    @Test
    void m2_024_saveOrUpdateRejectsStepBackward() {
        stubOrder();
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(existingAt("D5"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveOrUpdate(EXCEPTION_ID, dtoWithStep("D2")));

        assertEquals(ResultCode.EIGHT_D_STEP_INVALID.getCode(), ex.getCode());
    }

    @Test
    void m2_025_saveOrUpdateRejectsStepJump() {
        stubOrder();
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(existingAt("D2"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveOrUpdate(EXCEPTION_ID, dtoWithStep("D5")));

        assertEquals(ResultCode.EIGHT_D_STEP_JUMP.getCode(), ex.getCode());
    }

    @Test
    void saveOrUpdateAllowsSameStepEdit() {
        stubOrder();
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(existingAt("D3"));

        EightDVO vo = service.saveOrUpdate(EXCEPTION_ID, dtoWithStep("D3"));

        assertEquals("D3", vo.getCurrentStep());
    }

    @Test
    void saveOrUpdateAllowsSingleStepAdvance() {
        stubOrder();
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(existingAt("D3"));

        EightDVO vo = service.saveOrUpdate(EXCEPTION_ID, dtoWithStep("D4"));

        assertEquals("D4", vo.getCurrentStep());
    }

    @Test
    void nextStepAutoCreatesD1WhenMissing() {
        // D0 阶段 nextStep 不允许直接推进，需先提交 D1 团队信息
        stubOrder();
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(null);
        when(eightdMapper.selectByExceptionIdIgnoreDeleted(EXCEPTION_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.nextStep(EXCEPTION_ID));
        assertTrue(ex.getMessage().contains("D1") && ex.getMessage().contains("提交"),
                "D0 阶段 nextStep 应拦截并要求先通过团队提交推进，实际消息：" + ex.getMessage());
    }

    @Test
    void saveOrUpdateD8SetsCapaStatusInProgress() {
        // 业务语义：到达 D8 后 capaStatus 仍为「进行中」，待 D8 闭环审批通过后才置「已完成」
        stubOrder(); // status=处理中（非已闭环）
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(existingAt("D7"));

        service.saveOrUpdate(EXCEPTION_ID, dtoWithStep("D8"));

        ArgumentCaptor<ExceptionOrder> orderCaptor = ArgumentCaptor.forClass(ExceptionOrder.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        assertEquals("进行中", orderCaptor.getValue().getCapaStatus());
    }

    @Test
    void saveOrUpdateNonD8KeepsCapaStatusInProgress() {
        stubOrder();
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(existingAt("D5"));

        service.saveOrUpdate(EXCEPTION_ID, dtoWithStep("D6"));

        ArgumentCaptor<ExceptionOrder> orderCaptor = ArgumentCaptor.forClass(ExceptionOrder.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        assertEquals("进行中", orderCaptor.getValue().getCapaStatus());
    }

    @Test
    void nextStepToD8KeepsCapaStatusInProgress() {
        // nextStep 推进到 D8 时 currentStep 应为 D8，capaStatus 仍为「进行中」（待 D8 闭环审批）
        stubOrder();
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(fullRecordAt("D7"));

        EightDVO vo = service.nextStep(EXCEPTION_ID);

        assertEquals("D8", vo.getCurrentStep());
        ArgumentCaptor<ExceptionOrder> orderCaptor = ArgumentCaptor.forClass(ExceptionOrder.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        assertEquals("进行中", orderCaptor.getValue().getCapaStatus());
    }

    // ==================== M2-020 / M2-021 / M2-022：nextStep 推进序列与边界守卫 ====================

    private static Exception8d fullRecordAt(String step) {
        Exception8d rec = existingAt(step);
        rec.setD1Team("团队");
        rec.setD2ProblemDesc("问题描述");
        rec.setD3Containment("围堵措施");
        rec.setD4RootCause("根因");
        rec.setD5Corrective("纠正措施");
        rec.setD6Implementation("实施");
        rec.setD7Preventive("预防措施");
        rec.setD8Closure("闭环");
        return rec;
    }

    @Test
    void m2_020_nextStepAdvancesD2ThroughD7() {
        // 从 D2 起逐次调用 nextStep，验证 currentStep 按 D2→D3→…→D7 递增并落库
        stubOrder();
        Exception8d rec = fullRecordAt("D2");
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(rec);
        when(eightdMapper.updateById(any())).thenReturn(1);

        String[] path = {"D2", "D3", "D4", "D5", "D6", "D7"};
        for (int i = 0; i < path.length - 1; i++) {
            String expectedNext = path[i + 1];
            service.nextStep(EXCEPTION_ID);
            ArgumentCaptor<Exception8d> cap = ArgumentCaptor.forClass(Exception8d.class);
            verify(eightdMapper).updateById(cap.capture());
            assertEquals(expectedNext, cap.getValue().getCurrentStep());
            clearInvocations(eightdMapper, orderMapper);
        }
        assertEquals("D7", rec.getCurrentStep());
    }

    @Test
    void m2_021_nextStepRejectsEmptyStepContent() {
        // 当前步骤 D2 内容为空，nextStep 应抛 400「请先填写 D2 内容」
        stubOrder();
        Exception8d rec = existingAt("D2");
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(rec);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.nextStep(EXCEPTION_ID));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("请先填写 D2 内容"));
    }

    @Test
    void m2_022_nextStepFromD8Rejects() {
        // 已到达 D8（最后一步），再 nextStep 应抛 EIGHT_D_ALREADY_CLOSED(2007)
        stubOrder();
        Exception8d rec = fullRecordAt("D8");
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(rec);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.nextStep(EXCEPTION_ID));
        assertEquals(ResultCode.EIGHT_D_ALREADY_CLOSED.getCode(), ex.getCode());
    }

    @Test
    void m2_027_saveOrUpdateRejectsInvalidStep() {
        // 非 STEPS 集合内的步骤字符串，saveOrUpdate 应抛 400「无效的 8D 步骤」
        stubOrder();
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(existingAt("D3"));

        EightDSaveDTO badDto = dtoWithStep("DX");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveOrUpdate(EXCEPTION_ID, badDto));

        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("无效的 8D 步骤"),
                "非法步骤应提示无效，实际消息：" + ex.getMessage());
    }

    private static LoginUser sampleUser() {
        return LoginUser.builder()
                .userId(1001L)
                .account("qem01")
                .realName("质量工程师")
                .roleCode("R04")
                .plantCode(PlantCode.SZ)
                .build();
    }
}
