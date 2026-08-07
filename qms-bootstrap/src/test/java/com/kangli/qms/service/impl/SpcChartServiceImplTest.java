package com.kangli.qms.service.impl;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.spc.entity.SpcCoefficient;
import com.kangli.qms.domain.spc.entity.SpcControlLimit;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcSubgroup;
import com.kangli.qms.domain.spc.mapper.SpcCoefficientMapper;
import com.kangli.qms.domain.spc.mapper.SpcControlLimitMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcSampleMapper;
import com.kangli.qms.domain.spc.mapper.SpcSubgroupMapper;
import com.kangli.qms.service.spc.impl.SpcChartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SpcChartServiceImpl 控制限计算测试（对应测试方案 M4-003 + 疑似缺陷 D4 控制限侧）。
 *
 * <p>规则：子组数 <2 清空控制限并返回 null；≥2 按图型查系数计算。
 * Xbar-R：XbarUCL=xBarBar+A2·rBar，RUCL=D4·rBar；Xbar-s：XbarUCL=xBarBar+A3·sBar，SUCL=B4·sBar。
 * D4 验证：subgroupSize 越界致系数 null -> INTERNAL_ERROR(500)。</p>
 */
class SpcChartServiceImplTest {

    private SpcSubgroupMapper subgroupMapper;
    private SpcParameterMapper parameterMapper;
    private SpcControlLimitMapper controlLimitMapper;
    private SpcSampleMapper sampleMapper;
    private SpcCoefficientMapper coefficientMapper;
    private SpcChartServiceImpl service;

    @BeforeEach
    void setUp() {
        subgroupMapper = mock(SpcSubgroupMapper.class);
        parameterMapper = mock(SpcParameterMapper.class);
        controlLimitMapper = mock(SpcControlLimitMapper.class);
        sampleMapper = mock(SpcSampleMapper.class);
        coefficientMapper = mock(SpcCoefficientMapper.class);
        service = new SpcChartServiceImpl(subgroupMapper, parameterMapper, controlLimitMapper,
                sampleMapper, coefficientMapper);
    }

    private SpcParameter param(int n, String chartType) {
        SpcParameter p = new SpcParameter();
        p.setId(1L);
        p.setSubgroupSize(n);
        p.setChartType(chartType);
        p.setIsDeleted((short) 0);
        return p;
    }

    private List<SpcSubgroup> subgroups(int count, BigDecimal mean, BigDecimal range, BigDecimal std) {
        List<SpcSubgroup> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            SpcSubgroup s = new SpcSubgroup();
            s.setId((long) i);
            s.setMeanValue(mean);
            s.setRangeValue(range);
            s.setStdDev(std);
            s.setSampleTime(LocalDateTime.of(2026, 1, i, 9, 0));
            s.setSubgroupStatus("已完成");
            s.setPlantCode("SZ");
            list.add(s);
        }
        return list;
    }

    /** M4-003：子组数 <2 时清空控制限记录并返回 null。 */
    @Test
    void m4_003_recalcControlLimitsReturnsNullWhenFewerThanTwoSubgroups() {
        when(parameterMapper.selectById(1L)).thenReturn(param(5, "Xbar-R"));
        when(subgroupMapper.selectList(any())).thenReturn(subgroups(1, new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("0.790569")));

        SpcControlLimit result = service.recalcControlLimits(1L, "SZ");

        assertNull(result);
        verify(controlLimitMapper).delete(any());
    }

    /** Xbar-R 控制限公式：xBarBar=10, rBar=2, A2=0.577, D4=2.114, D3L=0。 */
    @Test
    void m4_xbarRControlLimitFormula() {
        when(parameterMapper.selectById(1L)).thenReturn(param(5, "Xbar-R"));
        when(subgroupMapper.selectList(any())).thenReturn(subgroups(5, new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("0.790569")));
        SpcCoefficient coef = new SpcCoefficient();
        coef.setN(5);
        coef.setA2(new BigDecimal("0.577"));
        coef.setD4(new BigDecimal("2.114"));
        coef.setD3L(BigDecimal.ZERO);
        when(coefficientMapper.selectById(any())).thenReturn(coef);

        SpcControlLimit cl = service.recalcControlLimits(1L, "SZ");

        assertEquals(11.154, cl.getXbarUcl().doubleValue(), 1e-3); // 10 + 0.577×2
        assertEquals(8.846, cl.getXbarLcl().doubleValue(), 1e-3);  // 10 - 0.577×2
        assertEquals(4.228, cl.getRUcl().doubleValue(), 1e-3);     // 2.114×2
        verify(controlLimitMapper).insert(any(SpcControlLimit.class));
    }

    /** Xbar-s 控制限公式：xBarBar=10, sBar=0.790569, A3=1.427, B4=2.089, B3=0。 */
    @Test
    void m4_xbarSControlLimitFormula() {
        when(parameterMapper.selectById(1L)).thenReturn(param(5, "Xbar-s"));
        when(subgroupMapper.selectList(any())).thenReturn(subgroups(5, new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("0.790569")));
        SpcCoefficient coef = new SpcCoefficient();
        coef.setN(5);
        coef.setA2(new BigDecimal("0.577"));
        coef.setD4(new BigDecimal("2.114"));
        coef.setD3L(BigDecimal.ZERO);
        coef.setA3(new BigDecimal("1.427"));
        coef.setB4(new BigDecimal("2.089"));
        coef.setB3(BigDecimal.ZERO);
        when(coefficientMapper.selectById(any())).thenReturn(coef);

        SpcControlLimit cl = service.recalcControlLimits(1L, "SZ");

        assertEquals(11.128, cl.getXbarUcl().doubleValue(), 1e-3); // 10 + 1.427×0.790569
        assertEquals(1.652, cl.getSUcl().doubleValue(), 1e-3);     // 2.089×0.790569
    }

    /** D4：subgroupSize 越界，系数查表 null -> INTERNAL_ERROR(500)。 */
    @Test
    void d4_coefficientNullForOutOfRangeNThrowsInternalError() {
        when(parameterMapper.selectById(1L)).thenReturn(param(13, "Xbar-R"));
        when(subgroupMapper.selectList(any())).thenReturn(subgroups(5, new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("0.790569")));
        when(coefficientMapper.selectById(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.recalcControlLimits(1L, "SZ"));
        assertEquals(ResultCode.INTERNAL_ERROR.getCode(), ex.getCode());
    }
}
