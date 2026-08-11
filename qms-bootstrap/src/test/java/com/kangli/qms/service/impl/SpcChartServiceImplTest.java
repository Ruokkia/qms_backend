package com.kangli.qms.service.impl;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.spc.entity.SpcCoefficient;
import com.kangli.qms.domain.spc.entity.SpcControlLimit;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcSample;
import com.kangli.qms.domain.spc.entity.SpcSubgroup;
import com.kangli.qms.domain.spc.mapper.SpcCoefficientMapper;
import com.kangli.qms.domain.spc.mapper.SpcControlLimitMapper;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcProcessMapper;
import com.kangli.qms.domain.spc.mapper.SpcSampleMapper;
import com.kangli.qms.domain.spc.mapper.SpcSubgroupMapper;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandard;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardItem;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.domain.spc.entity.SpcProcess;
import com.kangli.qms.service.spc.dto.SpcChartDataDTO;
import com.kangli.qms.service.spc.impl.SpcChartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SpcChartServiceImpl 控制图公式测试（对应测试方案 M4-003 / M4-004）。
 *
 * <p>固化公式：
 * 控制限 Xbar-R：XbarUcl/Lcl = x̄̄ ± A2·R̄；RUcl/Lcl = D4·R̄ / D3·R̄；CL = x̄̄(或 R̄)。
 * 控制限 Xbar-s：XbarUcl/Lcl = x̄̄ ± A3·s̄；SUcl/Lcl = B4·s̄ / B3·s̄；CL = x̄̄(或 s̄)。
 * 子组统计量：mean() 对 meanValue/rangeValue/stdDev 取算术均值（scale=6, HALF_UP）。
 * 过程能力（图内）：σ_within = R̄/d2 (Xbar-R) 或 s̄/c4 (Xbar-s)；
 * σ_overall = stdev(全部样本)；Cp=(USL-LSL)/(6σ_within)；Cpk=min(USL-x̄̄,x̄̄-LSL)/(3σ_within)。
 * 另覆盖：子组数<2 删除控制限并返回 null；系数缺失抛 INTERNAL_ERROR；
 * 控制限缺失时临时兜底 computeTempControlLimits 与主路径公式一致。</p>
 */
@ExtendWith(MockitoExtension.class)
class SpcChartServiceImplTest {

    private static final int SCALE = 6;
    private static final String XBAR_R = "Xbar-R";
    private static final String XBAR_S = "Xbar-s";

    @Mock
    private SpcSubgroupMapper subgroupMapper;
    @Mock
    private SpcParameterMapper parameterMapper;
    @Mock
    private SpcControlLimitMapper controlLimitMapper;
    @Mock
    private SpcSampleMapper sampleMapper;
    @Mock
    private SpcCoefficientMapper coefficientMapper;
    @Mock
    private SpcProcessMapper processMapper;
    @Mock
    private FaiInspectionStandardMapper faiStandardMapper;
    @Mock
    private FaiInspectionStandardItemMapper faiItemMapper;

    @InjectMocks
    private SpcChartServiceImpl chartService;

    /** n=5 的标准 SPC 系数（已知值，便于手算校验）。 */
    private SpcCoefficient coef;

    @BeforeEach
    void setUp() {
        coef = new SpcCoefficient();
        coef.setN(5);
        coef.setA2(new BigDecimal("0.577"));
        coef.setD4(new BigDecimal("2.114"));
        coef.setD3L(BigDecimal.ZERO);
        coef.setA3(new BigDecimal("1.427"));
        coef.setB4(new BigDecimal("2.089"));
        coef.setB3(BigDecimal.ZERO);
        coef.setD2(new BigDecimal("2.326"));
        coef.setC4(new BigDecimal("0.940"));
    }

    // ───────────────────────── M4-003 控制限计算（Xbar-R） ─────────────────────────

    @Test
    void m4_003_xbarR_controlLimits() {
        SpcParameter param = param("Xbar-R", 5);
        List<SpcSubgroup> subs = subgroups(
                new BigDecimal("10.00"), new BigDecimal("0.04"), new BigDecimal("0.012"),
                new BigDecimal("10.02"), new BigDecimal("0.05"), new BigDecimal("0.015"),
                new BigDecimal("9.98"), new BigDecimal("0.03"), new BigDecimal("0.013"));

        when(parameterMapper.selectById(1L)).thenReturn(param);
        when(subgroupMapper.selectList(any())).thenReturn(subs);
        when(coefficientMapper.selectById(5)).thenReturn(coef);
        when(controlLimitMapper.delete(any())).thenReturn(0);
        when(controlLimitMapper.insert(any())).thenReturn(1);

        SpcControlLimit cl = chartService.recalcControlLimits(1L, "SZ", null, null);

        // x̄̄ = (10.00+10.02+9.98)/3 = 10.000000 ; R̄ = (0.04+0.05+0.03)/3 = 0.040000
        BigDecimal xBarBar = bd("10.000000");
        BigDecimal rBar = bd("0.040000");
        assertEquals(xBarBar, cl.getXbarCl());
        assertEquals(rBar, cl.getRCl());
        // XbarUcl = x̄̄ + A2·R̄ = 10 + 0.577·0.04 = 10.023080
        assertEquals(bd("10.023080"), cl.getXbarUcl());
        // XbarLcl = 10 - 0.023080 = 9.976920
        assertEquals(bd("9.976920"), cl.getXbarLcl());
        // RUcl = D4·R̄ = 2.114·0.04 = 0.084560
        assertEquals(bd("0.084560"), cl.getRUcl());
        // RLcl = D3·R̄ = 0
        assertEquals(bd("0.000000"), cl.getRLcl());
        assertEquals(XBAR_R, cl.getChartType());
        assertEquals(3, cl.getSubgroupCount());
    }

    // ───────────────────────── M4-003 控制限计算（Xbar-s） ─────────────────────────

    @Test
    void m4_003_xbarS_controlLimits() {
        SpcParameter param = param("Xbar-s", 5);
        // s̄ = (0.012+0.015+0.013)/3 = 0.040000/3 = 0.013333
        List<SpcSubgroup> subs = subgroups(
                new BigDecimal("10.00"), new BigDecimal("0.04"), new BigDecimal("0.012"),
                new BigDecimal("10.02"), new BigDecimal("0.05"), new BigDecimal("0.015"),
                new BigDecimal("9.98"), new BigDecimal("0.03"), new BigDecimal("0.013"));

        when(parameterMapper.selectById(1L)).thenReturn(param);
        when(subgroupMapper.selectList(any())).thenReturn(subs);
        when(coefficientMapper.selectById(5)).thenReturn(coef);
        when(controlLimitMapper.delete(any())).thenReturn(0);
        when(controlLimitMapper.insert(any())).thenReturn(1);

        SpcControlLimit cl = chartService.recalcControlLimits(1L, "MZ", null, null);

        BigDecimal xBarBar = bd("10.000000");
        BigDecimal sBar = bd("0.013333");
        assertEquals(xBarBar, cl.getXbarCl());
        assertEquals(sBar, cl.getSCl());
        // XbarUcl = x̄̄ + A3·s̄ = 10 + 1.427·0.013333 = 10.019026
        assertEquals(bd("10.019026"), cl.getXbarUcl());
        // XbarLcl = 10 - 0.019026 = 9.980974
        assertEquals(bd("9.980974"), cl.getXbarLcl());
        // SUcl = B4·s̄ = 2.089·0.013333 = 0.027853
        assertEquals(bd("0.027853"), cl.getSUcl());
        // SLcl = B3·s̄ = 0
        assertEquals(bd("0.000000"), cl.getSLcl());
        assertEquals(XBAR_S, cl.getChartType());
    }

    // ───────────────── M4-003 子组数 < 2：删除控制限并返回 null ─────────────────

    @Test
    void m4_003_lessThanTwoSubgroups_returnsNullAndDeletes() {
        SpcParameter param = param("Xbar-R", 5);
        List<SpcSubgroup> subs = subgroups(
                new BigDecimal("10.00"), new BigDecimal("0.04"), new BigDecimal("0.012"));

        when(parameterMapper.selectById(1L)).thenReturn(param);
        when(subgroupMapper.selectList(any())).thenReturn(subs);
        when(controlLimitMapper.delete(any())).thenReturn(1);

        SpcControlLimit cl = chartService.recalcControlLimits(1L, "SZ", "产线", "P001");

        assertNull(cl, "子组数<2 应返回 null（已删除该维度控制限）");
    }

    // ───────────────── M4-003 系数缺失：抛 INTERNAL_ERROR ─────────────────

    @Test
    void m4_003_missingCoefficient_throwsInternalError() {
        SpcParameter param = param("Xbar-R", 5);
        List<SpcSubgroup> subs = subgroups(
                new BigDecimal("10.00"), new BigDecimal("0.04"), new BigDecimal("0.012"),
                new BigDecimal("10.02"), new BigDecimal("0.05"), new BigDecimal("0.015"));

        SpcCoefficient broken = new SpcCoefficient();
        broken.setN(5);
        broken.setA2(null); // A2 缺失

        when(parameterMapper.selectById(1L)).thenReturn(param);
        when(subgroupMapper.selectList(any())).thenReturn(subs);
        when(coefficientMapper.selectById(5)).thenReturn(broken);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> chartService.recalcControlLimits(1L, "SZ", null, null));
        assertEquals(ResultCode.INTERNAL_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("SPC 系数缺失"));
    }

    // ───────────────── M4-004 子组统计量聚合（mean()） ─────────────────

    @Test
    void m4_004_meanAggregation() throws Exception {
        Method mean = SpcChartServiceImpl.class.getDeclaredMethod("mean", List.class);
        mean.setAccessible(true);

        // 对 meanValue 列表取均值
        List<BigDecimal> meanValues = Arrays.asList(
                new BigDecimal("10.00"), new BigDecimal("10.02"), new BigDecimal("9.98"));
        BigDecimal result = (BigDecimal) mean.invoke(chartService, meanValues);
        assertEquals(bd("10.000000"), result);

        // 对 rangeValue 列表取均值
        List<BigDecimal> ranges = Arrays.asList(
                new BigDecimal("0.04"), new BigDecimal("0.05"), new BigDecimal("0.03"));
        assertEquals(bd("0.040000"), (BigDecimal) mean.invoke(chartService, ranges));

        // 对 stdDev 列表取均值（Xbar-s 的 s̄）
        List<BigDecimal> stdDevs = Arrays.asList(
                new BigDecimal("0.012"), new BigDecimal("0.015"), new BigDecimal("0.013"));
        assertEquals(bd("0.013333"), (BigDecimal) mean.invoke(chartService, stdDevs));
    }

    // ───────────────── M4-004 图内能力指数 σ_within / Cp / Cpk（Xbar-R） ─────────────────

    @Test
    void m4_004_capabilityIndex_xbarR() throws Exception {
        SpcChartDataDTO dto = new SpcChartDataDTO();
        dto.setParamId(1L);
        dto.setChartType(XBAR_R);
        dto.setSubgroupSize(5);
        dto.setUpperSpecLimit(new BigDecimal("10.10"));
        dto.setLowerSpecLimit(new BigDecimal("9.90"));
        // x̄̄ = 10.000000 ; R̄ = 0.040000 ; σ_within = R̄/d2 = 0.04/2.326 = 0.017197... -> 0.017197
        // Cp = (10.10-9.90)/(6·0.017197) = 0.20/0.103183 = 1.9385 -> 1.9385
        // Cpk = (10.10-10.00)/(3·0.017197) = 0.10/0.051592 = 1.9385 (对称)
        List<SpcSubgroup> subs = subgroups(
                new BigDecimal("10.00"), new BigDecimal("0.04"), new BigDecimal("0.012"),
                new BigDecimal("10.02"), new BigDecimal("0.05"), new BigDecimal("0.015"),
                new BigDecimal("9.98"), new BigDecimal("0.03"), new BigDecimal("0.013"));

        when(coefficientMapper.selectById(5)).thenReturn(coef);
        // σ_overall 样本：使整体均值≈10.00，标准差≈0.0172
        when(sampleMapper.selectList(any())).thenReturn(samples(
                "9.98", "10.00", "10.02", "10.01", "9.99",
                "9.97", "10.03", "10.00", "10.01", "9.99",
                "9.99", "10.00", "10.01", "9.98", "10.02"));

        invokeCalculateCapabilityIndex(dto, subs);

        // σ_within = R̄/d2 = 0.04/2.326 ≈ 0.017198；Cp = (USL-LSL)/(6σ) ≈ 1.938
        assertEquals(0.017198, dto.getSigmaWithin(), 1e-5);
        assertEquals(1.938, dto.getCp(), 1e-3);
        assertEquals(1.938, dto.getCpk(), 1e-3);
        // σ_overall 应 > 0 且 Pp/Ppk 已填充
        assertTrue(dto.getSigmaOverall() != null && dto.getSigmaOverall() > 0);
        assertTrue(dto.getPp() != null);
        assertTrue(dto.getPpk() != null);
    }

    // ───────────────── M4-004 图内能力指数 σ_within（Xbar-s：s̄/c4） ─────────────────

    @Test
    void m4_004_capabilityIndex_xbarS_sigmaWithin() throws Exception {
        SpcChartDataDTO dto = new SpcChartDataDTO();
        dto.setParamId(1L);
        dto.setChartType(XBAR_S);
        dto.setSubgroupSize(5);
        dto.setUpperSpecLimit(new BigDecimal("10.10"));
        dto.setLowerSpecLimit(new BigDecimal("9.90"));
        // s̄ = 0.013333 ; σ_within = s̄/c4 = 0.013333/0.940 = 0.014184... -> 0.014184
        List<SpcSubgroup> subs = subgroups(
                new BigDecimal("10.00"), new BigDecimal("0.04"), new BigDecimal("0.012"),
                new BigDecimal("10.02"), new BigDecimal("0.05"), new BigDecimal("0.015"),
                new BigDecimal("9.98"), new BigDecimal("0.03"), new BigDecimal("0.013"));

        when(coefficientMapper.selectById(5)).thenReturn(coef);
        when(sampleMapper.selectList(any())).thenReturn(samples(
                "9.98", "10.00", "10.02", "10.01", "9.99",
                "9.97", "10.03", "10.00", "10.01", "9.99",
                "9.99", "10.00", "10.01", "9.98", "10.02"));

        invokeCalculateCapabilityIndex(dto, subs);

        assertEquals(0.014184, dto.getSigmaWithin(), 1e-5);
        assertTrue(dto.getCp() != null && dto.getCp() > 0);
    }

    // ───────────────── M4-004 能力指数边界：子组<2 / 规格限缺失 / n 越界静默 return ─────────────────

    @Test
    void m4_004_capabilityIndex_edgeCases_silentReturn() throws Exception {
        // 子组 < 2
        SpcChartDataDTO d1 = new SpcChartDataDTO();
        d1.setUpperSpecLimit(new BigDecimal("10.10"));
        d1.setLowerSpecLimit(new BigDecimal("9.90"));
        d1.setSubgroupSize(5);
        invokeCalculateCapabilityIndex(d1, Collections.singletonList(
                subgroup(new BigDecimal("10.00"), new BigDecimal("0.04"), new BigDecimal("0.012"))));
        assertNull(d1.getCp(), "子组<2 应静默跳过，Cp 不计算");

        // 规格限缺失
        SpcChartDataDTO d2 = new SpcChartDataDTO();
        d2.setSubgroupSize(5);
        invokeCalculateCapabilityIndex(d2, subgroups(
                new BigDecimal("10.00"), new BigDecimal("0.04"), new BigDecimal("0.012"),
                new BigDecimal("10.02"), new BigDecimal("0.05"), new BigDecimal("0.015")));
        assertNull(d2.getCp(), "规格限缺失应静默跳过，Cp 不计算");

        // n 越界（n=1）
        SpcChartDataDTO d3 = new SpcChartDataDTO();
        d3.setUpperSpecLimit(new BigDecimal("10.10"));
        d3.setLowerSpecLimit(new BigDecimal("9.90"));
        d3.setSubgroupSize(1);
        invokeCalculateCapabilityIndex(d3, subgroups(
                new BigDecimal("10.00"), new BigDecimal("0.04"), new BigDecimal("0.012"),
                new BigDecimal("10.02"), new BigDecimal("0.05"), new BigDecimal("0.015")));
        assertNull(d3.getCp(), "n 越界应静默跳过，Cp 不计算");
    }

    // ───────────────── M4-004 临时控制限兜底（computeTempControlLimits，公式与主路径一致） ─────────────────

    @Test
    void m4_004_computeTempControlLimits_xbarR() throws Exception {
        SpcChartDataDTO dto = new SpcChartDataDTO();
        dto.setParamId(1L);
        List<SpcSubgroup> subs = subgroups(
                new BigDecimal("10.00"), new BigDecimal("0.04"), new BigDecimal("0.012"),
                new BigDecimal("10.02"), new BigDecimal("0.05"), new BigDecimal("0.015"),
                new BigDecimal("9.98"), new BigDecimal("0.03"), new BigDecimal("0.013"));

        when(coefficientMapper.selectById(5)).thenReturn(coef);

        invokeComputeTempControlLimits(dto, subs, 5, XBAR_R);

        assertEquals(bd("10.023080"), dto.getXbarUcl());
        assertEquals(bd("9.976920"), dto.getXbarLcl());
        assertEquals(bd("10.000000"), dto.getXbarCl());
        assertEquals(bd("0.084560"), dto.getRUcl());
        assertEquals(bd("0.040000"), dto.getRCl());
        assertEquals(bd("0.000000"), dto.getRLcl());
    }

    // ───────────────── 辅助方法 ─────────────────

    private SpcParameter param(String chartType, int n) {
        SpcParameter p = new SpcParameter();
        p.setId(1L);
        p.setChartType(chartType);
        p.setSubgroupSize(n);
        p.setIsDeleted((short) 0);
        return p;
    }

    /** 每 3 个 BigDecimal 构造一个子组 (meanValue, rangeValue, stdDev)。 */
    private List<SpcSubgroup> subgroups(BigDecimal... vals) {
        List<SpcSubgroup> list = new ArrayList<>();
        for (int i = 0; i < vals.length; i += 3) {
            list.add(subgroup(vals[i], vals[i + 1], vals[i + 2]));
        }
        return list;
    }

    private SpcSubgroup subgroup(BigDecimal mean, BigDecimal range, BigDecimal stdDev) {
        SpcSubgroup s = new SpcSubgroup();
        s.setId((long) (Math.random() * 1e9));
        s.setMeanValue(mean);
        s.setRangeValue(range);
        s.setStdDev(stdDev);
        s.setSubgroupStatus("已完成");
        return s;
    }

    private List<SpcSample> samples(String... vals) {
        List<SpcSample> list = new ArrayList<>();
        long id = 1L;
        for (String v : vals) {
            SpcSample s = new SpcSample();
            s.setId(id++);
            s.setSubgroupId(1L);
            s.setSampleValue(new BigDecimal(v));
            s.setIsDeleted((short) 0);
            list.add(s);
        }
        return list;
    }

    private BigDecimal bd(String v) {
        return new BigDecimal(v).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private void invokeCalculateCapabilityIndex(SpcChartDataDTO dto, List<SpcSubgroup> subs) throws Exception {
        Method m = SpcChartServiceImpl.class.getDeclaredMethod(
                "calculateCapabilityIndex", SpcChartDataDTO.class, List.class);
        m.setAccessible(true);
        m.invoke(chartService, dto, subs);
    }

    private void invokeComputeTempControlLimits(SpcChartDataDTO dto, List<SpcSubgroup> subs,
                                                int n, String chartType) throws Exception {
        Method m = SpcChartServiceImpl.class.getDeclaredMethod(
                "computeTempControlLimits", SpcChartDataDTO.class, List.class, int.class, String.class);
        m.setAccessible(true);
        m.invoke(chartService, dto, subs, n, chartType);
    }
}
