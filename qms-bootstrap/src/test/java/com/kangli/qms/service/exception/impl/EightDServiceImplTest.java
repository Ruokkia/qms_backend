package com.kangli.qms.service.exception.impl;

import com.kangli.qms.common.BusinessException;
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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
        // 对应缺陷：8D 流程已发起但未建记录，nextStep 应自动建 D1 而非 404
        stubOrder();
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(null);
        when(eightdMapper.selectByExceptionIdIgnoreDeleted(EXCEPTION_ID)).thenReturn(null);

        EightDVO vo = service.nextStep(EXCEPTION_ID);

        assertEquals("D1", vo.getCurrentStep());
        ArgumentCaptor<Exception8d> captor = ArgumentCaptor.forClass(Exception8d.class);
        verify(eightdMapper).insert(captor.capture());
        assertEquals("D1", captor.getValue().getCurrentStep());
        assertEquals(EXCEPTION_ID, captor.getValue().getExceptionId());
    }

    @Test
    void saveOrUpdateD8SetsCapaStatusCompletedRegardlessOfCloseStatus() {
        // 业务语义：到达 D8 即代表已完成，与异常单是否闭环无关
        stubOrder(); // status=处理中（非已闭环）
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(existingAt("D7"));

        service.saveOrUpdate(EXCEPTION_ID, dtoWithStep("D8"));

        ArgumentCaptor<ExceptionOrder> orderCaptor = ArgumentCaptor.forClass(ExceptionOrder.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        assertEquals("已完成", orderCaptor.getValue().getCapaStatus());
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
    void nextStepToD8SetsCapaStatusCompleted() {
        // nextStep 推进到 D8 时 capaStatus 应为已完成（统一收敛于 saveOrUpdate 入口）
        stubOrder();
        when(eightdMapper.selectByExceptionId(EXCEPTION_ID)).thenReturn(existingAt("D7"));

        EightDVO vo = service.nextStep(EXCEPTION_ID);

        assertEquals("D8", vo.getCurrentStep());
        ArgumentCaptor<ExceptionOrder> orderCaptor = ArgumentCaptor.forClass(ExceptionOrder.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        assertEquals("已完成", orderCaptor.getValue().getCapaStatus());
    }
}
