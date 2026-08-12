package com.kangli.qms.service.spc.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.fai.mapper.FaiInspectionItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionRecordMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcProcess;
import com.kangli.qms.domain.spc.entity.SpcSample;
import com.kangli.qms.domain.spc.entity.SpcSubgroup;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcProcessMapper;
import com.kangli.qms.domain.spc.mapper.SpcSampleMapper;
import com.kangli.qms.domain.spc.mapper.SpcSubgroupMapper;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.service.spc.SpcCapabilityService;
import com.kangli.qms.service.spc.SpcChartService;
import com.kangli.qms.service.fai.FaiStandardService;
import com.kangli.qms.service.spc.dto.SpcPendingSampleAppendDTO;
import com.kangli.qms.service.spc.dto.SpcSubgroupResponse;
import com.kangli.qms.service.spc.dto.SpcSubgroupSaveDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SpcSubgroupServiceImpl 子组录入业务逻辑单测（对应测试方案 M4 子组录入缺口 #3）。
 *
 * <p>覆盖：save（参数校验/跨厂区 FORBIDDEN/样本数/batchNo+barcode 必填/SPC 统计计算/快照/厂区）、
 * appendPendingSamples（状态机/仅首件导入可补/超量拒绝/补满翻转/跨厂区 FORBIDDEN）、
 * detail（存在/不存在/逻辑删除→NOT_FOUND）、remove（不存在/级联清理）。
 * 采用有参构造 + 全 mock 注入的纯单测范式。</p>
 */
class SpcSubgroupServiceImplTest {

    private ObjectMapper objectMapper;
    private SpcSubgroupMapper subgroupMapper;
    private SpcSampleMapper sampleMapper;
    private SpcParameterMapper parameterMapper;
    private SpcProcessMapper processMapper;
    private FaiInspectionRecordMapper faiRecordMapper;
    private FaiInspectionItemMapper faiItemMapper;
    private FaiInspectionStandardItemMapper faiStandardItemMapper;
    private FaiInspectionStandardMapper faiStandardMapper;
    private MaterialInspectionMapper matMapper;
    private FinishedGoodsInspectionMapper fgMapper;
    private SpcChartService chartService;
    private SpcCapabilityService capabilityService;
    private SpcSubgroupServiceImpl service;

    private static final Long PARAM_ID = 100L;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        subgroupMapper = mock(SpcSubgroupMapper.class);
        sampleMapper = mock(SpcSampleMapper.class);
        parameterMapper = mock(SpcParameterMapper.class);
        processMapper = mock(SpcProcessMapper.class);
        faiRecordMapper = mock(FaiInspectionRecordMapper.class);
        faiItemMapper = mock(FaiInspectionItemMapper.class);
        faiStandardItemMapper = mock(FaiInspectionStandardItemMapper.class);
        faiStandardMapper = mock(FaiInspectionStandardMapper.class);
        matMapper = mock(MaterialInspectionMapper.class);
        fgMapper = mock(FinishedGoodsInspectionMapper.class);
        chartService = mock(SpcChartService.class);
        capabilityService = mock(SpcCapabilityService.class);
        service = new SpcSubgroupServiceImpl(objectMapper, subgroupMapper, sampleMapper, parameterMapper,
                processMapper, faiRecordMapper, faiItemMapper, faiStandardItemMapper, faiStandardMapper,
                matMapper, fgMapper, chartService, capabilityService, mock(FaiStandardService.class));

        // recalc / capability 默认返回 null（非 void 方法，remove 会无条件调用 recalcControlLimits）
        when(chartService.recalcControlLimits(anyLong(), any(), any(), any())).thenReturn(null);
        when(capabilityService.recalcCapability(anyLong(), any(), any(), any(), any())).thenReturn(null);
        doNothing().when(capabilityService).clear(anyLong(), any(), any(), any());
        // subgroupCount / genSubgroupNo 依赖的 selectCount 默认 0（不触发 recalc，保持单测纯净）
        when(subgroupMapper.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        // 无 ThreadLocal 需要清理
    }

    // ---------- 工具 ----------

    private LoginUser loginUser() {
        return LoginUser.builder()
                .userId(1L)
                .account("qc01")
                .realName("质检员一")
                .roleCode("R02")
                .plantCode(PlantCode.SZ)
                .build();
    }

    private SpcParameter param(Long id) {
        SpcParameter p = new SpcParameter();
        p.setId(id);
        p.setProcessId(5L);
        p.setParamCode("P001");
        p.setParamName("外径");
        p.setUnit("mm");
        p.setUpperSpecLimit(new BigDecimal("15.00"));
        p.setLowerSpecLimit(new BigDecimal("9.00"));
        p.setTargetValue(new BigDecimal("12.00"));
        p.setSubgroupSize(5);
        p.setChartType("Xbar-R");
        p.setIsActive("是");
        p.setPlantCode("SZ");
        p.setPlantName("深圳");
        p.setIsDeleted((short) 0);
        return p;
    }

    private List<BigDecimal> fiveSamples() {
        return Arrays.asList(new BigDecimal("10"), new BigDecimal("12"), new BigDecimal("11"),
                new BigDecimal("13"), new BigDecimal("9"));
    }

    /** 为 detail() 内部查询打桩：selectById(paramId)->param、selectById(processId)->process、sampleList->samples */
    private void stubDetail(SpcSubgroup sub, SpcParameter p, List<SpcSample> samples) {
        when(subgroupMapper.selectById(sub.getId())).thenReturn(sub);
        when(sampleMapper.selectList(any())).thenReturn(samples);
        when(parameterMapper.selectById(p.getId())).thenReturn(p);
        SpcProcess proc = new SpcProcess();
        proc.setId(5L);
        proc.setProcessName("车削");
        when(processMapper.selectById(5L)).thenReturn(proc);
    }

    // ================= save =================

    @Test
    void save_paramNotFound_throwsNotFound() {
        when(parameterMapper.selectById(PARAM_ID)).thenReturn(null);
        SpcSubgroupSaveDTO dto = new SpcSubgroupSaveDTO();
        dto.setParamId(PARAM_ID);
        dto.setSampleValues(fiveSamples());
        dto.setBatchNo("B1");
        dto.setBarcode("C1");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(dto, loginUser()));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void save_paramOfOtherPlant_throwsForbidden() {
        SpcParameter p = param(PARAM_ID);
        p.setPlantCode("MZ"); // 与登录用户 SZ 不同厂区
        when(parameterMapper.selectById(PARAM_ID)).thenReturn(p);
        SpcSubgroupSaveDTO dto = new SpcSubgroupSaveDTO();
        dto.setParamId(PARAM_ID);
        dto.setSampleValues(fiveSamples());
        dto.setBatchNo("B1");
        dto.setBarcode("C1");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(dto, loginUser()));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void save_emptyValues_throwsBadRequest() {
        when(parameterMapper.selectById(PARAM_ID)).thenReturn(param(PARAM_ID));
        SpcSubgroupSaveDTO dto = new SpcSubgroupSaveDTO();
        dto.setParamId(PARAM_ID);
        dto.setSampleValues(Collections.emptyList());
        dto.setBatchNo("B1");
        dto.setBarcode("C1");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(dto, loginUser()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void save_sizeMismatch_throwsBadRequest() {
        when(parameterMapper.selectById(PARAM_ID)).thenReturn(param(PARAM_ID)); // subgroupSize=5
        SpcSubgroupSaveDTO dto = new SpcSubgroupSaveDTO();
        dto.setParamId(PARAM_ID);
        dto.setSampleValues(Arrays.asList(new BigDecimal("10"), new BigDecimal("12"),
                new BigDecimal("11"), new BigDecimal("13"))); // 4 个
        dto.setBatchNo("B1");
        dto.setBarcode("C1");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(dto, loginUser()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void save_missingBatchNo_throwsBadRequest() {
        when(parameterMapper.selectById(PARAM_ID)).thenReturn(param(PARAM_ID));
        SpcSubgroupSaveDTO dto = new SpcSubgroupSaveDTO();
        dto.setParamId(PARAM_ID);
        dto.setSampleValues(fiveSamples());
        dto.setBarcode("C1");
        // batchNo 不设置

        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(dto, loginUser()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void save_missingBarcode_throwsBadRequest() {
        when(parameterMapper.selectById(PARAM_ID)).thenReturn(param(PARAM_ID));
        SpcSubgroupSaveDTO dto = new SpcSubgroupSaveDTO();
        dto.setParamId(PARAM_ID);
        dto.setSampleValues(fiveSamples());
        dto.setBatchNo("B1");
        // barcode 不设置

        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(dto, loginUser()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void save_normal_computesStatsAndFillsSnapshot() {
        SpcParameter p = param(PARAM_ID);
        when(parameterMapper.selectById(PARAM_ID)).thenReturn(p);
        when(subgroupMapper.insert(any(SpcSubgroup.class))).thenAnswer(inv -> {
            inv.getArgument(0, SpcSubgroup.class).setId(200L);
            return 1;
        });
        when(sampleMapper.insert(any(SpcSample.class))).thenReturn(1);

        SpcSubgroup saved = new SpcSubgroup();
        saved.setId(200L);
        saved.setParamId(PARAM_ID);
        saved.setSubgroupNo("SG-SZ-P001-20260811-0001");
        saved.setSampleCount(5);
        saved.setMeanValue(new BigDecimal("11.000000"));
        saved.setRangeValue(new BigDecimal("4.000000"));
        saved.setStdDev(new BigDecimal("1.414214"));
        saved.setSourceType("手动录入");
        saved.setSubgroupStatus("已完成");
        saved.setPlantCode("SZ");
        saved.setPlantName("深圳");
        saved.setIsDeleted((short) 0);
        saved.setParamCode(p.getParamCode());
        saved.setUnit(p.getUnit());
        saved.setTargetValue(p.getTargetValue());
        saved.setUpperSpecLimit(p.getUpperSpecLimit());
        saved.setLowerSpecLimit(p.getLowerSpecLimit());
        saved.setItemType("PRODUCT");
        saved.setItemCode("P-001");
        // detail() 内查询打桩
        stubDetail(saved, p, Collections.emptyList());

        SpcSubgroupSaveDTO dto = new SpcSubgroupSaveDTO();
        dto.setParamId(PARAM_ID);
        dto.setSampleValues(fiveSamples());
        dto.setBatchNo("B1");
        dto.setBarcode("C1");
        dto.setItemType("PRODUCT");
        dto.setItemCode("P-001");
        dto.setMaterialName("齿轮");

        SpcSubgroupResponse resp = service.save(dto, loginUser());

        assertNotNull(resp);
        assertEquals(200L, resp.getId());
        assertEquals(PARAM_ID, resp.getParamId());
        // mean = (10+12+11+13+9)/5 = 11
        assertEquals(0, new BigDecimal("11.000000").compareTo(resp.getMeanValue()));
        // range = 13 - 9 = 4
        assertEquals(0, new BigDecimal("4.000000").compareTo(resp.getRangeValue()));
        // 样本标准差（n-1）= sqrt(2) ≈ 1.414214
        assertEquals(0, new BigDecimal("1.414214").compareTo(resp.getStdDev()));
        // 快照 & 厂区
        assertEquals("SZ", resp.getPlantCode());
        assertEquals("深圳", resp.getPlantName());
        assertEquals(p.getParamCode(), resp.getParamCode());
        assertEquals(p.getUnit(), resp.getUnit());
        assertEquals(0, p.getTargetValue().compareTo(resp.getTargetValue()));
        assertEquals(0, p.getUpperSpecLimit().compareTo(resp.getUpperSpecLimit()));
        assertEquals(0, p.getLowerSpecLimit().compareTo(resp.getLowerSpecLimit()));
        assertEquals("已完成", resp.getSubgroupStatus());
        assertEquals("PRODUCT", resp.getItemType());
    }

    // ================= appendPendingSamples =================

    private SpcSubgroup pendingSubgroup(Long id, int sampleCount) {
        SpcSubgroup sg = new SpcSubgroup();
        sg.setId(id);
        sg.setParamId(PARAM_ID);
        sg.setSubgroupStatus("待补样本");
        sg.setSourceType("首件自动导入");
        sg.setSampleCount(sampleCount);
        sg.setVersion(1);
        sg.setPlantCode("SZ");
        sg.setPlantName("深圳");
        sg.setIsDeleted((short) 0);
        return sg;
    }

    @Test
    void append_subgroupNotFound_throwsNotFound() {
        when(subgroupMapper.selectById(300L)).thenReturn(null);
        SpcPendingSampleAppendDTO dto = new SpcPendingSampleAppendDTO();
        dto.setSampleValues(Arrays.asList(new BigDecimal("10")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.appendPendingSamples(300L, dto, loginUser()));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void append_otherPlant_throwsForbidden() {
        SpcSubgroup sg = pendingSubgroup(300L, 3);
        sg.setPlantCode("MZ");
        when(subgroupMapper.selectById(300L)).thenReturn(sg);
        SpcPendingSampleAppendDTO dto = new SpcPendingSampleAppendDTO();
        dto.setSampleValues(Arrays.asList(new BigDecimal("10")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.appendPendingSamples(300L, dto, loginUser()));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void append_nonFaiPendingSubgroup_throwsBadRequest() {
        // 手动录入创建的待补子组不允许补录
        SpcSubgroup sg = pendingSubgroup(300L, 3);
        sg.setSourceType("手动录入");
        when(subgroupMapper.selectById(300L)).thenReturn(sg);
        SpcPendingSampleAppendDTO dto = new SpcPendingSampleAppendDTO();
        dto.setSampleValues(Arrays.asList(new BigDecimal("10")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.appendPendingSamples(300L, dto, loginUser()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(subgroupMapper, never()).updateById(any());
    }

    @Test
    void append_completedSubgroup_throwsBadRequest() {
        SpcSubgroup sg = pendingSubgroup(300L, 5);
        sg.setSubgroupStatus("已完成");
        when(subgroupMapper.selectById(300L)).thenReturn(sg);
        SpcPendingSampleAppendDTO dto = new SpcPendingSampleAppendDTO();
        dto.setSampleValues(Arrays.asList(new BigDecimal("10")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.appendPendingSamples(300L, dto, loginUser()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void append_exceedsSubgroupSize_throwsBadRequest() {
        SpcSubgroup sg = pendingSubgroup(300L, 4); // 已 4
        when(subgroupMapper.selectById(300L)).thenReturn(sg);
        when(parameterMapper.selectById(PARAM_ID)).thenReturn(param(PARAM_ID)); // subgroupSize=5
        // existing 来自 DB 查询，构造 4 个；补 2 个 -> 6 > 5 触发超量
        List<SpcSample> existing = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            SpcSample e = new SpcSample();
            e.setSampleNo(i);
            existing.add(e);
        }
        when(sampleMapper.selectList(any())).thenReturn(existing); // existing.size()=4，补 2 -> 6 > 5

        SpcPendingSampleAppendDTO dto = new SpcPendingSampleAppendDTO();
        dto.setSampleValues(Arrays.asList(new BigDecimal("10"), new BigDecimal("11")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.appendPendingSamples(300L, dto, loginUser()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(subgroupMapper, never()).updateById(any());
    }

    @Test
    void append_fillsUp_flipsToCompletedAndRecalcs() {
        SpcSubgroup sg = pendingSubgroup(300L, 3); // 已 3，补 2 -> 5 = subgroupSize
        when(subgroupMapper.selectById(300L)).thenReturn(sg);
        when(parameterMapper.selectById(PARAM_ID)).thenReturn(param(PARAM_ID));
        when(subgroupMapper.updateById(any(SpcSubgroup.class))).thenReturn(1);
        when(sampleMapper.insert(any(SpcSample.class))).thenReturn(1);

        // 第一次 selectList：existing（3 个）；第二次 selectList：allValues（5 个）
        List<SpcSample> existing = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            SpcSample e = new SpcSample();
            e.setSampleNo(i);
            e.setSampleValue(new BigDecimal("10").add(BigDecimal.valueOf(i)));
            existing.add(e);
        }
        List<SpcSample> all = new ArrayList<>(existing);
        all.add(new SpcSample() {{ setSampleNo(4); setSampleValue(new BigDecimal("14")); }});
        all.add(new SpcSample() {{ setSampleNo(5); setSampleValue(new BigDecimal("12")); }});
        java.util.concurrent.atomic.AtomicInteger ansCall = new java.util.concurrent.atomic.AtomicInteger(0);
        when(sampleMapper.selectList(any())).thenAnswer(inv -> {
            int n = ansCall.getAndIncrement();
            return n == 0 ? existing : all;
        });

        // detail() 内查询打桩：detail(300) 复用同一 sg 对象（append 已将其改写为已完成并补算统计量），
        // 注意不要覆盖上面的 subgroupMapper.selectById(300L)->sg 桩
        when(parameterMapper.selectById(PARAM_ID)).thenReturn(param(PARAM_ID));
        SpcProcess proc = new SpcProcess();
        proc.setId(5L);
        proc.setProcessName("车削");
        when(processMapper.selectById(5L)).thenReturn(proc);

        SpcPendingSampleAppendDTO dto = new SpcPendingSampleAppendDTO();
        dto.setSampleValues(Arrays.asList(new BigDecimal("14"), new BigDecimal("12")));

        SpcSubgroupResponse resp = service.appendPendingSamples(300L, dto, loginUser());

        assertNotNull(resp);
        assertEquals("已完成", resp.getSubgroupStatus());
        assertEquals(5, resp.getSampleCount());
        // 补满后子组完成，应触发 updateById 落库
        verify(subgroupMapper).updateById(any(SpcSubgroup.class));
    }

    @Test
    void append_partialStaysPending() {
        SpcSubgroup sg = pendingSubgroup(300L, 3); // 补 1 -> 4 < 5
        when(subgroupMapper.selectById(300L)).thenReturn(sg);
        when(parameterMapper.selectById(PARAM_ID)).thenReturn(param(PARAM_ID));
        when(subgroupMapper.updateById(any(SpcSubgroup.class))).thenReturn(1);
        when(sampleMapper.insert(any(SpcSample.class))).thenReturn(1);

        List<SpcSample> existing = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            SpcSample e = new SpcSample();
            e.setSampleNo(i);
            existing.add(e);
        }
        List<SpcSample> all = new ArrayList<>(existing);
        all.add(new SpcSample() {{ setSampleNo(4); }});
        java.util.concurrent.atomic.AtomicInteger c2 = new java.util.concurrent.atomic.AtomicInteger(0);
        when(sampleMapper.selectList(any())).thenAnswer(inv -> c2.getAndIncrement() == 0 ? existing : all);

        SpcSubgroup after = pendingSubgroup(300L, 4);
        after.setSubgroupStatus("待补样本");
        when(subgroupMapper.selectById(after.getId())).thenReturn(after);
        when(parameterMapper.selectById(PARAM_ID)).thenReturn(param(PARAM_ID));
        SpcProcess proc = new SpcProcess();
        proc.setId(5L);
        proc.setProcessName("车削");
        when(processMapper.selectById(5L)).thenReturn(proc);

        SpcPendingSampleAppendDTO dto = new SpcPendingSampleAppendDTO();
        dto.setSampleValues(Arrays.asList(new BigDecimal("14")));

        SpcSubgroupResponse resp = service.appendPendingSamples(300L, dto, loginUser());
        assertEquals("待补样本", resp.getSubgroupStatus());
        assertEquals(4, resp.getSampleCount());
    }

    // ================= detail =================

    @Test
    void detail_notExist_throwsNotFound() {
        when(subgroupMapper.selectById(400L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(400L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void detail_logicDeleted_throwsNotFound() {
        SpcSubgroup sg = new SpcSubgroup();
        sg.setId(300L);
        sg.setIsDeleted((short) 1);
        when(subgroupMapper.selectById(300L)).thenReturn(sg);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(300L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void detail_exists_returnsResponse() {
        SpcSubgroup sg = new SpcSubgroup();
        sg.setId(300L);
        sg.setParamId(PARAM_ID);
        sg.setSubgroupStatus("已完成");
        sg.setIsDeleted((short) 0);
        stubDetail(sg, param(PARAM_ID), Collections.emptyList());

        SpcSubgroupResponse resp = service.detail(300L);
        assertNotNull(resp);
        assertEquals(300L, resp.getId());
    }

    // ================= remove =================

    @Test
    void remove_notExist_throwsNotFound() {
        when(subgroupMapper.selectById(400L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.remove(400L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void remove_normal_deletesAndCascades() {
        SpcSubgroup sg = new SpcSubgroup();
        sg.setId(300L);
        sg.setParamId(PARAM_ID);
        sg.setPlantCode("SZ");
        sg.setItemType("PRODUCT");
        sg.setItemCode("P-001");
        sg.setBatchNo("B1");
        sg.setIsDeleted((short) 0);
        when(subgroupMapper.selectById(300L)).thenReturn(sg);
        when(sampleMapper.delete(any())).thenReturn(1);
        when(subgroupMapper.deleteById(300L)).thenReturn(1);

        service.remove(300L);

        verify(sampleMapper).delete(any(LambdaQueryWrapper.class));
        verify(subgroupMapper).deleteById(300L);
        // count<20 -> 调 chartService.recalc + capabilityService.clear
        verify(chartService).recalcControlLimits(anyLong(), any(), any(), any());
        verify(capabilityService).clear(anyLong(), any(), any(), any());
    }
}
