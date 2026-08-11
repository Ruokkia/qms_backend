package com.kangli.qms.service.fai.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.kangli.qms.common.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandard;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandardItem;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardHistoryMapper;
import com.kangli.qms.domain.fai.mapper.FaiStandardApprovalMapper;
import com.kangli.qms.domain.spc.entity.SpcParameter;
import com.kangli.qms.domain.spc.entity.SpcProcess;
import com.kangli.qms.domain.spc.mapper.SpcParameterMapper;
import com.kangli.qms.domain.spc.mapper.SpcProcessMapper;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.service.fai.dto.FaiStandardItemRequest;
import com.kangli.qms.service.fai.dto.FaiStandardSaveRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M3-011 SPC 绑定校验纯 mock 单测：标准项强制绑定 SPC 时的三项校验。
 * 通过 public 门面 createStandard（内部首个调用 validateAndResolveSpcItems）触发校验，
 * 校验失败时抛 BAD_REQUEST 提前返回，不执行后续持久化，因此无需连库。
 */
class FaiStandardServiceImplTest {

    private static final String PLANT = "SZ";

    @BeforeAll
    static void initTableInfo() {
        // 纯 mock 环境下初始化实体元数据，使 LambdaQueryWrapper 可用（同既有测试写法）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "dummy");
        TableInfoHelper.initTableInfo(assistant, FaiInspectionStandard.class);
        TableInfoHelper.initTableInfo(assistant, FaiInspectionStandardItem.class);
    }

    private LoginUser szUser() {
        return LoginUser.builder().userId(9L).realName("测试员").plantCode(PlantCode.SZ).build();
    }

    private FaiStandardItemRequest spcItem(Long spcParamId, String isRequired) {
        FaiStandardItemRequest it = new FaiStandardItemRequest();
        it.setSpcParameterId(spcParamId);
        it.setIsRequired(isRequired);
        return it;
    }

    private FaiStandardSaveRequest reqWith(String processCode, List<FaiStandardItemRequest> items) {
        FaiStandardSaveRequest req = new FaiStandardSaveRequest();
        req.setMaterialCode("M-SPC-001");
        req.setProcessName("装配");
        req.setProcessCode(processCode);
        req.setItems(items);
        return req;
    }

    private SpcParameter param(Long id, String isActive, String plant, Long processId) {
        SpcParameter p = new SpcParameter();
        p.setId(id);
        p.setIsActive(isActive);
        p.setPlantCode(plant);
        p.setProcessId(processId);
        p.setIsDeleted((short) 0); // 逻辑删除字段，saveItems 回填时会访问
        return p;
    }

    private SpcProcess process(Long id, String plant, String processCode) {
        SpcProcess p = new SpcProcess();
        p.setId(id);
        p.setPlantCode(plant);
        p.setProcessCode(processCode);
        p.setIsDeleted((short) 0);
        return p;
    }

    /** spcEnabled=否（不绑定 SPC 参数）：校验通过，createStandard 正常完成不抛异常 */
    @Test
    void createStandard_noSpcBinding_passesValidation() {
        FaiInspectionStandardMapper standardMapper = mock(FaiInspectionStandardMapper.class);
        FaiInspectionStandardItemMapper itemMapper = mock(FaiInspectionStandardItemMapper.class);
        FaiInspectionStandardHistoryMapper historyMapper = mock(FaiInspectionStandardHistoryMapper.class);

        when(standardMapper.selectOne(any())).thenReturn(null); // nextVersion → 1
        when(standardMapper.selectById(any())).thenReturn(new FaiInspectionStandard()); // buildSnapshot
        when(itemMapper.selectList(any())).thenReturn(Collections.emptyList());

        FaiStandardServiceImpl service = new FaiStandardServiceImpl(
                standardMapper, itemMapper, historyMapper,
                mock(FaiStandardApprovalMapper.class),
                mock(SpcParameterMapper.class), mock(SpcProcessMapper.class));

        FaiStandardSaveRequest req = reqWith("ASM", List.of(spcItem(null, "是")));
        assertDoesNotThrow(() -> service.createStandard(req, szUser()));
    }

    /** spcEnabled=是 但 SPC 参数不存在 / 未启用 → 400 */
    @Test
    void createStandard_spcParamInvalid_rejects() {
        SpcParameterMapper spcParamMapper = mock(SpcParameterMapper.class);
        when(spcParamMapper.selectById(100L)).thenReturn(param(100L, "否", PLANT, 99L));

        FaiStandardServiceImpl service = new FaiStandardServiceImpl(
                mock(FaiInspectionStandardMapper.class), mock(FaiInspectionStandardItemMapper.class),
                mock(FaiInspectionStandardHistoryMapper.class), mock(FaiStandardApprovalMapper.class),
                spcParamMapper, mock(SpcProcessMapper.class));

        FaiStandardSaveRequest req = reqWith("ASM", List.of(spcItem(100L, "是")));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createStandard(req, szUser()));
        assertEquals(400, ex.getCode());
    }

    /** spcEnabled=是 且参数有效，但参数所属工序 processCode 与首件工序不匹配 → 400 */
    @Test
    void createStandard_spcParamProcessMismatch_rejects() {
        SpcParameterMapper spcParamMapper = mock(SpcParameterMapper.class);
        SpcProcessMapper spcProcessMapper = mock(SpcProcessMapper.class);
        when(spcParamMapper.selectById(200L)).thenReturn(param(200L, "是", PLANT, 99L));
        when(spcProcessMapper.selectById(99L)).thenReturn(process(99L, PLANT, "WELD")); // 与 ASM 不一致

        FaiStandardServiceImpl service = new FaiStandardServiceImpl(
                mock(FaiInspectionStandardMapper.class), mock(FaiInspectionStandardItemMapper.class),
                mock(FaiInspectionStandardHistoryMapper.class), mock(FaiStandardApprovalMapper.class),
                spcParamMapper, spcProcessMapper);

        FaiStandardSaveRequest req = reqWith("ASM", List.of(spcItem(200L, "是")));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createStandard(req, szUser()));
        assertEquals(400, ex.getCode());
    }

    /** spcEnabled=是 且参数、工序均匹配 → 校验通过，createStandard 正常完成 */
    @Test
    void createStandard_spcParamValid_completes() {
        SpcParameterMapper spcParamMapper = mock(SpcParameterMapper.class);
        SpcProcessMapper spcProcessMapper = mock(SpcProcessMapper.class);
        when(spcParamMapper.selectById(300L)).thenReturn(param(300L, "是", PLANT, 88L));
        when(spcProcessMapper.selectById(88L)).thenReturn(process(88L, PLANT, "ASM")); // 匹配请求工序

        FaiInspectionStandardMapper standardMapper = mock(FaiInspectionStandardMapper.class);
        FaiInspectionStandardItemMapper itemMapper = mock(FaiInspectionStandardItemMapper.class);
        FaiInspectionStandardHistoryMapper historyMapper = mock(FaiInspectionStandardHistoryMapper.class);
        when(standardMapper.selectOne(any())).thenReturn(null);
        when(standardMapper.selectById(any())).thenReturn(new FaiInspectionStandard());
        when(itemMapper.selectList(any())).thenReturn(Collections.emptyList());

        FaiStandardServiceImpl service = new FaiStandardServiceImpl(
                standardMapper, itemMapper, historyMapper, mock(FaiStandardApprovalMapper.class),
                spcParamMapper, spcProcessMapper);

        FaiStandardSaveRequest req = reqWith("ASM", List.of(spcItem(300L, "是")));
        assertDoesNotThrow(() -> service.createStandard(req, szUser()));
    }
}
