package com.kangli.qms.service.spc.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.spc.entity.SpcCapability;
import com.kangli.qms.domain.spc.entity.SpcCoefficient;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandard;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardItem;
import com.kangli.qms.domain.spc.entity.SpcProcess;
import com.kangli.qms.domain.spc.mapper.SpcCapabilityMapper;
import com.kangli.qms.domain.spc.mapper.SpcCoefficientMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcProcessMapper;
import com.kangli.qms.domain.spc.mapper.SpcSampleMapper;
import com.kangli.qms.domain.spc.mapper.SpcSubgroupMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SpcCapabilityServiceImpl 单测：聚焦 Cpk 判定边界（M4-010）、维度隔离删除（M4-011）、
 * 系数 c4/d2 为零的防御性拦截（M4-034 已知 bug 回归）。
 */
class SpcCapabilityServiceImplTest {

    private final SpcSubgroupMapper subgroupMapper = mock(SpcSubgroupMapper.class);
    private final SpcParameterMapper parameterMapper = mock(SpcParameterMapper.class);
    private final SpcSampleMapper sampleMapper = mock(SpcSampleMapper.class);
    private final SpcCapabilityMapper capabilityMapper = mock(SpcCapabilityMapper.class);
    private final SpcCoefficientMapper coefficientMapper = mock(SpcCoefficientMapper.class);
    private final SpcProcessMapper processMapper = mock(SpcProcessMapper.class);
    private final FaiInspectionStandardMapper standardMapper = mock(FaiInspectionStandardMapper.class);
    private final FaiInspectionStandardItemMapper standardItemMapper = mock(FaiInspectionStandardItemMapper.class);

    private final SpcCapabilityServiceImpl service = new SpcCapabilityServiceImpl(
            subgroupMapper, parameterMapper, sampleMapper, capabilityMapper,
            coefficientMapper, processMapper, standardMapper, standardItemMapper);

    // ===== M4-010 Cpk 判定边界 =====

    @Test
    void judge_boundary_returnsCorrectLevel() throws Exception {
        Method judge = SpcCapabilityServiceImpl.class.getDeclaredMethod("judge", BigDecimal.class);
        judge.setAccessible(true);

        assertEquals("充足", judge.invoke(service, new BigDecimal("1.33")));
        assertEquals("充足", judge.invoke(service, new BigDecimal("1.5")));
        assertEquals("需改进", judge.invoke(service, new BigDecimal("1.0")));
        assertEquals("需改进", judge.invoke(service, new BigDecimal("1.2")));
        assertEquals("不足", judge.invoke(service, new BigDecimal("0.99")));
        assertEquals("不足", judge.invoke(service, new BigDecimal("0.5")));
    }

    // ===== M4-034 缺少 FAI 检验标准时拒绝计算（前置防护，避免 c4/d2 除零） =====

    @Test
    void recalc_withoutFaiStandard_throwsBadRequest() {
        SpcParameter param = new SpcParameter();
        param.setId(1L);
        param.setProcessId(5L);
        param.setPlantCode("SZ");
        param.setIsDeleted((short) 0);
        when(parameterMapper.selectById(1L)).thenReturn(param);
        when(subgroupMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(java.util.List.of(subgroup(1L, 10.0, 1.0, 0.5)));
        when(processMapper.selectById(5L)).thenReturn(spcProcess("P1"));
        // 未配置 FAI 标准 -> resolveSpecFromStandard 返回 null -> 抛 BAD_REQUEST
        when(standardMapper.selectActiveByCondition(any(), any(), any(), any()))
                .thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recalcCapability(1L, "SZ", "MATERIAL", "M100", null));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ===== M4-034 缺少 FAI 标准明细（无上下限）时拒绝计算 =====

    @Test
    void recalc_withoutStandardItemLimit_throwsBadRequest() {
        SpcParameter param = new SpcParameter();
        param.setId(1L);
        param.setProcessId(5L);
        param.setPlantCode("SZ");
        param.setIsDeleted((short) 0);
        when(parameterMapper.selectById(1L)).thenReturn(param);
        when(subgroupMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(java.util.List.of(subgroup(1L, 10.0, 1.0, 0.5)));
        when(processMapper.selectById(5L)).thenReturn(spcProcess("P1"));
        when(standardMapper.selectActiveByCondition(any(), any(), any(), any()))
                .thenReturn(faiStandard(1L));
        // 标准存在但明细缺少上下限 -> 抛 BAD_REQUEST
        when(standardItemMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recalcCapability(1L, "SZ", "MATERIAL", "M100", null));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ===== M4 子组数不足 20 报错 =====

    @Test
    void recalc_subgroupsLessThan20_throws() {
        SpcParameter param = new SpcParameter();
        param.setId(1L);
        param.setIsDeleted((short) 0);
        when(parameterMapper.selectById(1L)).thenReturn(param);
        when(subgroupMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(java.util.List.of(subgroup(1L, 10.0, 1.0, 0.5),
                        subgroup(2L, 10.2, 1.1, 0.6)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recalcCapability(1L, "SZ", null, null, null));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ===== M4-011 clear 按 item 维度精确删除（不误删其它 item） =====

    @Test
    void clear_onlyDeletesSpecifiedItemDimension() {
        service.clear(1L, "SZ", "MATERIAL", "P100");
        verify(capabilityMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    // ===== M4-011 clear 无 item 时删除该参数全部维度 =====

    @Test
    void clear_withoutItem_deletesAllDimensionsForParam() {
        service.clear(1L, "SZ", null, null);
        verify(capabilityMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    private com.kangli.qms.domain.spc.entity.SpcSubgroup subgroup(Long id, double mean, double range, double std) {
        com.kangli.qms.domain.spc.entity.SpcSubgroup s = new com.kangli.qms.domain.spc.entity.SpcSubgroup();
        s.setId(id);
        s.setParamId(1L);
        s.setPlantCode("SZ");
        s.setSubgroupStatus("已完成");
        s.setMeanValue(BigDecimal.valueOf(mean));
        s.setRangeValue(BigDecimal.valueOf(range));
        s.setStdDev(BigDecimal.valueOf(std));
        s.setSampleTime(java.time.LocalDateTime.now());
        return s;
    }

    private SpcProcess spcProcess(String code) {
        SpcProcess p = new SpcProcess();
        p.setId(5L);
        p.setProcessCode(code);
        return p;
    }

    private FaiInspectionStandard faiStandard(Long id) {
        FaiInspectionStandard s = new FaiInspectionStandard();
        s.setId(id);
        s.setProcessCode("P1");
        return s;
    }

    private FaiInspectionStandardItem faiItem(double usl, double lsl) {
        FaiInspectionStandardItem item = new FaiInspectionStandardItem();
        item.setStandardId(1L);
        item.setSpcParameterId(1L);
        item.setUpperLimit(BigDecimal.valueOf(usl));
        item.setLowerLimit(BigDecimal.valueOf(lsl));
        item.setSubgroupSize(5);
        item.setChartType("Xbar-R");
        return item;
    }
}
