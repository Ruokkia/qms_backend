package com.kangli.qms.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.exception.entity.Exception8d;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.entity.Exception8dStepLog;
import com.kangli.qms.domain.exception.mapper.Exception8dMapper;
import com.kangli.qms.domain.exception.mapper.Exception8dStepLogMapper;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.vo.EightDVO;
import com.kangli.qms.service.exception.ExceptionApprovalConfigService;
import com.kangli.qms.service.exception.dto.EightDSaveDTO;
import com.kangli.qms.service.exception.impl.EightDServiceImpl;
import com.kangli.qms.service.notification.NotificationConfigService;
import com.kangli.qms.service.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EightDServiceImpl 8D 状态机 / 留痕 / 权限单元测试。
 *
 * <p>覆盖：D1->D8 只进不退；空内容拒绝前进；到 D8 不提前置 capaStatus=已完成
 * （由统一闭环负责）；step 回退被拒；version 冲突被拒；非法步骤被拒；
 * save/nextStep 正确写入 8D 步骤留痕。</p>
 */
class EightDServiceImplTest {

    private Exception8dMapper exception8dMapper;
    private ExceptionOrderMapper exceptionOrderMapper;
    private Exception8dStepLogMapper stepLogMapper;
    private ExceptionApprovalConfigService approvalConfigService;
    private NotificationService notificationService;
    private SysUserMapper sysUserMapper;
    private ObjectMapper objectMapper;
    private EightDServiceImpl service;

    @BeforeEach
    void setUp() {
        exception8dMapper = mock(Exception8dMapper.class);
        exceptionOrderMapper = mock(ExceptionOrderMapper.class);
        stepLogMapper = mock(Exception8dStepLogMapper.class);
        approvalConfigService = mock(ExceptionApprovalConfigService.class);
        notificationService = mock(NotificationService.class);
        sysUserMapper = mock(SysUserMapper.class);
        objectMapper = mock(ObjectMapper.class);
        service = new EightDServiceImpl(exception8dMapper, exceptionOrderMapper, stepLogMapper,
                approvalConfigService, notificationService, mock(NotificationConfigService.class),
                sysUserMapper, objectMapper);
        // nextStep / saveOrUpdate 内含 assertRole，需注入 R06 登录用户
        LoginUserHolder.set(LoginUser.builder()
                .userId(6L).account("sz_mgr01").realName("测试经理")
                .roleCode("R06").plantCode(PlantCode.SZ).canSwitchArea(true).build());
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private ExceptionOrder order(Long id, String status, String capaStatus) {
        ExceptionOrder o = new ExceptionOrder();
        o.setId(id);
        o.setExceptionNo("EX-20260728-001");
        o.setStatus(status);
        o.setCapaStatus(capaStatus);
        o.setPlantCode("SZ");
        o.setPlantName("深圳");
        return o;
    }

    private Exception8d record(String currentStep, Integer version) {
        Exception8d r = new Exception8d();
        r.setId(1L);
        r.setExceptionId(1L);
        r.setCurrentStep(currentStep);
        r.setVersion(version);
        r.setIsDeleted((short) 0);
        r.setD1Team("team A");
        r.setD2ProblemDesc("problem");
        r.setD3Containment("containment");
        r.setD4RootCause("root cause");
        r.setD5Corrective("corrective");
        r.setD6Implementation("implementation");
        r.setD7Preventive("preventive");
        r.setD8Closure("closure");
        return r;
    }

    /** M2-020：nextStep D1->D2 前进，落库步骤前进。 */
    @Test
    void m2_020_nextStepAdvancesFromD1ToD2() {
        Exception8d r = record("D1", 1);
        when(exception8dMapper.selectByExceptionId(1L)).thenReturn(r);
        when(exceptionOrderMapper.selectById(1L)).thenReturn(order(1L, "待整改", "进行中"));

        EightDVO result = service.nextStep(1L);

        assertEquals("D2", result.getCurrentStep());
        verify(exception8dMapper).updateById(any(Exception8d.class));
        // 留痕：saveOrUpdate 写一条 SAVE + nextStep 末尾写一条 NEXT_STEP，共 2 条
        verify(stepLogMapper, times(2)).insert(any(Exception8dStepLog.class));
    }

    /** M2-021：当前步骤内容为空时拒绝前进。 */
    @Test
    void m2_021_nextStepRejectsEmptyContent() {
        Exception8d r = record("D1", 1);
        r.setD1Team(null);
        when(exception8dMapper.selectByExceptionId(1L)).thenReturn(r);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.nextStep(1L));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /**
     * M2-022：D8 时 nextStep 抛 EIGHT_D_ALREADY_CLOSED。
     * 已修复：getStepFieldContent 处理 D8 返回 closure 内容，空内容校验通过，
     * 到达 D8 末步后正确抛出 EIGHT_D_ALREADY_CLOSED（不可回退/不可再前进）。
     */
    @Test
    void m2_022_nextStepAtD8ThrowsAlreadyClosed() {
        Exception8d r = record("D8", 1);
        when(exception8dMapper.selectByExceptionId(1L)).thenReturn(r);
        when(exceptionOrderMapper.selectById(1L)).thenReturn(order(1L, "待整改", "进行中"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.nextStep(1L));
        assertEquals(ResultCode.EIGHT_D_ALREADY_CLOSED.getCode(), ex.getCode());
    }

    /**
     * M2-023：nextStep D7->D8 后不提前置 capaStatus=已完成。
     * 修复后 capaStatus 由统一闭环（close）负责写入「已完成」，
     * nextStep/saveOrUpdate 仅维持「进行中」，避免与 status 状态机脱节。
     */
    @Test
    void m2_023_nextStepToD8KeepsCapaStatusInProgress() {
        Exception8d r = record("D7", 1);
        when(exception8dMapper.selectByExceptionId(1L)).thenReturn(r);
        when(exceptionOrderMapper.selectById(1L)).thenReturn(order(1L, "待整改", "进行中"));

        EightDVO result = service.nextStep(1L);

        assertEquals("D8", result.getCurrentStep());
        ArgumentCaptor<ExceptionOrder> captor = ArgumentCaptor.forClass(ExceptionOrder.class);
        verify(exceptionOrderMapper, times(1)).updateById(captor.capture());
        assertEquals("进行中", captor.getValue().getCapaStatus());
    }

    /** M2-024：saveOrUpdate 拒绝步骤回退（只进不退）。 */
    @Test
    void m2_024_saveOrUpdateRejectsStepBackward() {
        Exception8d existing = record("D5", 1);
        when(exceptionOrderMapper.selectById(1L)).thenReturn(order(1L, "待整改", "进行中"));
        when(exception8dMapper.selectByExceptionId(1L)).thenReturn(existing);

        EightDSaveDTO dto = new EightDSaveDTO();
        dto.setCurrentStep("D2");
        dto.setVersion(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveOrUpdate(1L, dto));
        assertEquals(ResultCode.EIGHT_D_STEP_INVALID.getCode(), ex.getCode());
        verify(exception8dMapper, never()).updateById(any(Exception8d.class));
    }

    /** M2-025：version 不匹配 -> VERSION_CONFLICT。 */
    @Test
    void m2_025_saveOrUpdateRejectsVersionConflict() {
        Exception8d existing = record("D3", 2);
        when(exceptionOrderMapper.selectById(1L)).thenReturn(order(1L, "待整改", "进行中"));
        when(exception8dMapper.selectByExceptionId(1L)).thenReturn(existing);

        EightDSaveDTO dto = new EightDSaveDTO();
        dto.setCurrentStep("D3");
        dto.setVersion(1);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveOrUpdate(1L, dto));
        assertEquals(ResultCode.VERSION_CONFLICT.getCode(), ex.getCode());
        verify(exception8dMapper, never()).updateById(any(Exception8d.class));
    }

    /** M2-027：非法步骤（D9）-> BAD_REQUEST。 */
    @Test
    void m2_027_saveOrUpdateRejectsInvalidStep() {
        when(exceptionOrderMapper.selectById(anyLong())).thenReturn(order(1L, "待整改", "进行中"));
        when(exception8dMapper.selectByExceptionId(anyLong())).thenReturn(null);
        when(exception8dMapper.selectByExceptionIdIgnoreDeleted(anyLong())).thenReturn(null);

        EightDSaveDTO dto = new EightDSaveDTO();
        dto.setCurrentStep("D9");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveOrUpdate(1L, dto));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /** M2-028：saveOrUpdate 正常保存应写入一条 SAVE 留痕。 */
    @Test
    void m2_028_saveOrUpdateWritesSaveLog() {
        when(exceptionOrderMapper.selectById(1L)).thenReturn(order(1L, "待整改", "进行中"));
        when(exception8dMapper.selectByExceptionId(1L)).thenReturn(null);
        when(exception8dMapper.selectByExceptionIdIgnoreDeleted(1L)).thenReturn(null);

        EightDSaveDTO dto = new EightDSaveDTO();
        dto.setCurrentStep("D1");
        dto.setD1Team("团队X");

        service.saveOrUpdate(1L, dto);

        verify(stepLogMapper, times(1)).insert(any(Exception8dStepLog.class));
    }
}
