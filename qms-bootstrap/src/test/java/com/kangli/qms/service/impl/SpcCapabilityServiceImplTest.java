package com.kangli.qms.service.impl;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.spc.entity.SpcCoefficient;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcSample;
import com.kangli.qms.domain.spc.entity.SpcSubgroup;
import com.kangli.qms.domain.spc.mapper.SpcCapabilityMapper;
import com.kangli.qms.domain.spc.mapper.SpcCoefficientMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcProcessMapper;
import com.kangli.qms.domain.spc.mapper.SpcSampleMapper;
import com.kangli.qms.domain.spc.mapper.SpcSubgroupMapper;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandard;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardItem;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.domain.spc.entity.SpcProcess;
import com.kangli.qms.service.spc.dto.SpcCapabilityResultDTO;
import com.kangli.qms.service.spc.impl.SpcCapabilityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SpcCapabilityServiceImpl 过程能力指数测试（对应测试方案 M4-001 / M4-002 + 疑似缺陷 D4）。
 *
 * <p>固化公式：sigmaHat = Xbar-s 时 sBar/c4，Xbar-R 时 rBar/d2；
 * Cp=(USL-LSL)/(6σ)，Cpu=(USL-μ)/(3σ)，Cpl=(μ-LSL)/(3σ)，Cpk=min(Cpu,Cpl)；
 * 判定 cpk≥1.33 充足 / ≥1.0 需改进 / 否则不足。
 * 另验证 D4：subgroupSize 越界致系数查表 null -> INTERNAL_ERROR(500)；Xbar-s + c4=0 -> 除零异常(500)。</p>
 */
class SpcCapabilityServiceImplTest {

    private SpcSubgroupMapper subgroupMapper;
    private SpcParameterMapper parameterMapper;
    private SpcSampleMapper sampleMapper;
    private SpcCapabilityMapper capabilityMapper;
    private SpcCoefficientMapper coefficientMapper;
    private SpcProcessMapper processMapper;
    private FaiInspectionStandardMapper standardMapper;
    private FaiInspectionStandardItemMapper standardItemMapper;
    private SpcCapabilityServiceImpl service;

    @BeforeEach
    void setUp() {
        subgroupMapper = mock(SpcSubgroupMapper.class);
        parameterMapper = mock(SpcParameterMapper.class);
        sampleMapper = mock(SpcSampleMapper.class);
        capabilityMapper = mock(SpcCapabilityMapper.class);
        coefficientMapper = mock(SpcCoefficientMapper.class);
        processMapper = mock(SpcProcessMapper.class);
        standardMapper = mock(FaiInspectionStandardMapper.class);
        standardItemMapper = mock(FaiInspectionStandardItemMapper.class);
        service = new SpcCapabilityServiceImpl(subgroupMapper, parameterMapper, sampleMapper,
                capabilityMapper, coefficientMapper, processMapper, standardMapper, standardItemMapper);
    }

    private SpcParameter param(int n, String chartType, BigDecimal usl, BigDecimal lsl) {
        SpcParameter p = new SpcParameter();
        p.setId(1L);
        p.setSubgroupSize(n);
        p.setChartType(chartType);
        p.setUpperSpecLimit(usl);
        p.setLowerSpecLimit(lsl);
        p.setProcessId(1L);
        p.setIsDeleted((short) 0);
        return p;
    }

    /** 复用：mock 工序+FAI标准+标准项链路，resolveSpecFromStandard 解析规格限。 */
    private void mockSpecChain(String chartType, BigDecimal usl, BigDecimal lsl) {
        SpcProcess process = new SpcProcess();
        process.setId(1L);
        process.setProcessCode("装配");
        when(processMapper.selectById(1L)).thenReturn(process);
        FaiInspectionStandard standard = new FaiInspectionStandard();
        standard.setId(1L);
        when(standardMapper.selectActiveByCondition(any(), any(), any(), any())).thenReturn(standard);
        FaiInspectionStandardItem stdItem = new FaiInspectionStandardItem();
        stdItem.setUpperLimit(usl);
        stdItem.setLowerLimit(lsl);
        stdItem.setSubgroupSize(5);
        stdItem.setChartType(chartType);
        when(standardItemMapper.selectOne(any())).thenReturn(stdItem);
    }

    private List<SpcSubgroup> subgroups(int count, BigDecimal mean, BigDecimal range, BigDecimal std) {
        List<SpcSubgroup> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            SpcSubgroup s = new SpcSubgroup();
            s.setId((long) i);
            s.setParamId(1L);
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

    /** 100 个样本：每子组 [base-1, base-0.5, base, base+0.5, base+1]，对称分布。 */
    private List<SpcSample> samplesAround(int subgroups, int n, BigDecimal base) {
        BigDecimal[] offsets = {new BigDecimal("-1"), new BigDecimal("-0.5"),
                BigDecimal.ZERO, new BigDecimal("0.5"), new BigDecimal("1")};
        List<SpcSample> list = new ArrayList<>();
        long id = 1;
        for (int sg = 1; sg <= subgroups; sg++) {
            for (int k = 0; k < n; k++) {
                SpcSample s = new SpcSample();
                s.setId(id++);
                s.setSubgroupId((long) sg);
                s.setSampleValue(base.add(offsets[k]));
                list.add(s);
            }
        }
        return list;
    }

    /** M4-001：Xbar-R 路径 Cp/Cpk 数学正确性。sigmaHat=rBar/d2=2/2.326=0.859845，Cp=Cpk=1.1629。 */
    @Test
    void m4_001_xbarRCapabilityMath() {
        when(parameterMapper.selectById(1L)).thenReturn(param(5, "Xbar-R", new BigDecimal("13"), new BigDecimal("7")));
        when(subgroupMapper.selectList(any())).thenReturn(subgroups(20, new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("0.790569")));
        when(sampleMapper.selectList(any())).thenReturn(samplesAround(20, 5, new BigDecimal("10")));
        SpcCoefficient coef = new SpcCoefficient();
        coef.setN(5);
        coef.setD2(new BigDecimal("2.326"));
        coef.setC4(new BigDecimal("0.94"));
        when(coefficientMapper.selectById(any())).thenReturn(coef);
        mockSpecChain("Xbar-R", new BigDecimal("13"), new BigDecimal("7"));

        SpcCapabilityResultDTO r = service.recalcCapability(1L, "SZ", "PRODUCT", "MC-001", "LOT-001");

        assertEquals(1.1629, r.getCp().doubleValue(), 1e-4);
        assertEquals(1.1629, r.getCpk().doubleValue(), 1e-4);
        assertEquals(1.1629, r.getCpu().doubleValue(), 1e-4);
        assertEquals(1.1629, r.getCpl().doubleValue(), 1e-4);
        // 长期 sigmaLong=sqrt(50/99)=0.71067，Pp=Ppk=1.4071
        assertEquals(1.4071, r.getPp().doubleValue(), 2e-3);
        assertEquals(1.4071, r.getPpk().doubleValue(), 2e-3);
        assertEquals("需改进", r.getJudgment()); // 1.16 ∈ [1.0, 1.33)
        assertEquals(20, r.getSubgroupCount().intValue());
        assertEquals(100, r.getSampleCount().intValue());
    }

    /** M4-002：Xbar-s 路径 Cp/Cpk 数学正确性。sigmaHat=sBar/c4=0.790569/0.94=0.841031，Cp=1.189。 */
    @Test
    void m4_002_xbarSCapabilityMath() {
        when(parameterMapper.selectById(1L)).thenReturn(param(5, "Xbar-s", new BigDecimal("13"), new BigDecimal("7")));
        when(subgroupMapper.selectList(any())).thenReturn(subgroups(20, new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("0.790569")));
        when(sampleMapper.selectList(any())).thenReturn(samplesAround(20, 5, new BigDecimal("10")));
        SpcCoefficient coef = new SpcCoefficient();
        coef.setN(5);
        coef.setC4(new BigDecimal("0.94"));
        coef.setD2(new BigDecimal("2.326"));
        when(coefficientMapper.selectById(any())).thenReturn(coef);
        mockSpecChain("Xbar-s", new BigDecimal("13"), new BigDecimal("7"));

        SpcCapabilityResultDTO r = service.recalcCapability(1L, "SZ", "PRODUCT", "MC-001", "LOT-001");

        assertEquals(1.189, r.getCp().doubleValue(), 1e-3);
        assertEquals(1.189, r.getCpk().doubleValue(), 1e-3);
        assertEquals("需改进", r.getJudgment());
    }

    /** D4：subgroupSize=13 越界，系数查表 null -> INTERNAL_ERROR(500)。 */
    @Test
    void d4_coefficientNullForOutOfRangeNThrowsInternalError() {
        when(parameterMapper.selectById(1L)).thenReturn(param(13, "Xbar-R", new BigDecimal("13"), new BigDecimal("7")));
        when(subgroupMapper.selectList(any())).thenReturn(subgroups(20, new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("0.790569")));
        when(sampleMapper.selectList(any())).thenReturn(samplesAround(20, 5, new BigDecimal("10")));
        when(coefficientMapper.selectById(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.recalcCapability(1L, "SZ", null, null, null));
        assertEquals(ResultCode.INTERNAL_ERROR.getCode(), ex.getCode());
    }

    /** D4：Xbar-s 路径 c4=0（legacy-seed 不含 c4 列）-> sBar/0 除零异常(500)。 */
    @Test
    void d4_xbarSWithC4ZeroThrowsInternalError() {
        when(parameterMapper.selectById(1L)).thenReturn(param(5, "Xbar-s", new BigDecimal("13"), new BigDecimal("7")));
        when(subgroupMapper.selectList(any())).thenReturn(subgroups(20, new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("0.790569")));
        when(sampleMapper.selectList(any())).thenReturn(samplesAround(20, 5, new BigDecimal("10")));
        SpcCoefficient coef = new SpcCoefficient();
        coef.setN(5);
        coef.setC4(BigDecimal.ZERO);
        when(coefficientMapper.selectById(any())).thenReturn(coef);

        // D4 已修：c4=0 现被防御性校验拦截，抛 INTERNAL_ERROR 而非除零 ArithmeticException
        BusinessException ex = assertThrows(BusinessException.class, () -> service.recalcCapability(1L, "SZ", null, null, null));
        assertEquals(ResultCode.INTERNAL_ERROR.getCode(), ex.getCode());
    }

    /** 子组数 <20 -> BAD_REQUEST「子组数不足」。 */
    @Test
    void recalc_rejectsWhenSubgroupsLessThan20() {
        when(parameterMapper.selectById(1L)).thenReturn(param(5, "Xbar-R", new BigDecimal("13"), new BigDecimal("7")));
        when(subgroupMapper.selectList(any())).thenReturn(subgroups(19, new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("0.790569")));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.recalcCapability(1L, "SZ", null, null, null));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /** 规格上下限缺失 -> BAD_REQUEST。 */
    @Test
    void recalc_rejectsWhenSpecLimitsMissing() {
        when(parameterMapper.selectById(1L)).thenReturn(param(5, "Xbar-R", null, null));
        when(subgroupMapper.selectList(any())).thenReturn(subgroups(20, new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("0.790569")));
        when(sampleMapper.selectList(any())).thenReturn(samplesAround(20, 5, new BigDecimal("10")));
        SpcCoefficient coef = new SpcCoefficient();
        coef.setN(5);
        coef.setD2(new BigDecimal("2.326"));
        coef.setC4(new BigDecimal("0.94"));
        when(coefficientMapper.selectById(any())).thenReturn(coef);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.recalcCapability(1L, "SZ", null, null, null));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    /** 参数不存在 -> NOT_FOUND。 */
    @Test
    void recalc_rejectsWhenParameterMissing() {
        when(parameterMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.recalcCapability(1L, "SZ", null, null, null));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }
}
