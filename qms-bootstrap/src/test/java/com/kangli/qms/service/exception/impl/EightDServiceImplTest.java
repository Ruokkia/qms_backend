package com.kangli.qms.service.exception.impl;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.exception.dto.EightDSaveDTO;
import com.kangli.qms.domain.exception.entity.Exception8d;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.mapper.Exception8dMapper;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.vo.EightDVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 8D 报告「只进不退、逐步推进」约束的回归测试。
 * <p>对应安全用例 M2-024：PUT /eight-d 曾可直接回退/跳步绕过 nextStep，
 * 现由 saveOrUpdate 统一拦截（EIGHT_D_STEP_INVALID=2006 / EIGHT_D_STEP_JUMP=2009）。</p>
 */
class EightDServiceImplTest {

    private final Exception8dMapper eightdMapper = mock(Exception8dMapper.class);
    private final ExceptionOrderMapper orderMapper = mock(ExceptionOrderMapper.class);
    private final EightDServiceImpl service = new EightDServiceImpl(eightdMapper, orderMapper);

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
}
