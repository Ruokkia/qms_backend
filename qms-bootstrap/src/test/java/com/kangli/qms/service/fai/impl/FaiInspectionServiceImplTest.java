package com.kangli.qms.service.fai.impl;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.service.fai.dto.FaiSignatureRequest;
import com.kangli.qms.domain.fai.entity.FaiChangeTrigger;
import com.kangli.qms.domain.fai.entity.FaiInspectionItem;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.fai.mapper.FaiChangeTriggerMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionRecordMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.domain.fai.mapper.FaiSignatureMapper;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.fai.entity.FaiSignature;
import com.kangli.qms.service.admin.AuditLogService;
import com.kangli.qms.service.exception.ExceptionService;
import com.kangli.qms.service.fai.FaiStandardService;
import com.kangli.qms.service.fai.SignatureIntegrity;
import com.kangli.qms.service.fai.dto.FaiInspectionQuery;
import com.kangli.qms.service.fai.dto.FaiReportResponse;
import com.kangli.qms.service.spc.SpcSubgroupService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaiInspectionServiceImplTest {

    /** 初始化 MyBatis-Plus 的实体 TableInfo/lambda 缓存，使 LambdaQueryWrapper 在纯单元测试中可用 */
    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "init");
        TableInfoHelper.initTableInfo(assistant, FaiInspectionRecord.class);
    }

    @Test
    void signature_shouldRejectIncorrectCurrentUserPassword() {
        FaiInspectionRecordMapper recordMapper = mock(FaiInspectionRecordMapper.class);
        FaiInspectionRecord record = new FaiInspectionRecord();
        record.setId(101L);
        record.setInspectionResult("合格");
        when(recordMapper.selectById(101L)).thenReturn(record);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser signer = new SysUser();
        signer.setId(9L);
        signer.setRealName("测试员");
        signer.setStatus((short) 1);
        signer.setPasswordHash(new BCryptPasswordEncoder().encode("correct-password"));
        when(userMapper.selectById(9L)).thenReturn(signer);

        FaiInspectionServiceImpl service = new FaiInspectionServiceImpl(
                recordMapper,
                mock(FaiInspectionItemMapper.class),
                mock(FaiChangeTriggerMapper.class),
                mock(FaiInspectionStandardMapper.class),
                mock(FaiInspectionStandardItemMapper.class),
                mock(FaiSignatureMapper.class),
                mock(FaiStandardService.class),
                mock(SpcSubgroupService.class),
                mock(ExceptionService.class),
                userMapper,
                mock(AuditLogService.class));

        FaiSignatureRequest request = new FaiSignatureRequest();
        request.setFaiRecordId(101L);
        request.setSignerId("9");
        request.setSignType("检验签");
        request.setSignReason("检验完成");
        request.setPassword("1");

        LoginUser loginUser = LoginUser.builder().userId(9L).realName("测试员").build();

        assertThrows(BusinessException.class, () -> service.signature(request, loginUser));
    }

    /**
     * L30 + L16 回归：首件出现不合格明细时，autoJudge 应
     * 1) 将电子签名置为「未签」（合格→不合格回退失效）；
     * 2) 自动调用 createFromFai 建异常整改单。
     */
    @Test
    void judge_withFailingItem_invalidatesSignatureAndCreatesException() {
        FaiInspectionRecordMapper recordMapper = mock(FaiInspectionRecordMapper.class);
        FaiInspectionItemMapper itemMapper = mock(FaiInspectionItemMapper.class);
        FaiChangeTriggerMapper changeTriggerMapper = mock(FaiChangeTriggerMapper.class);
        FaiSignatureMapper signatureMapper = mock(FaiSignatureMapper.class);
        FaiInspectionStandardMapper standardMapper = mock(FaiInspectionStandardMapper.class);
        FaiInspectionStandardItemMapper standardItemMapper = mock(FaiInspectionStandardItemMapper.class);
        ExceptionService exceptionService = mock(ExceptionService.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);

        FaiInspectionRecord record = new FaiInspectionRecord();
        record.setId(1L);
        record.setChangeTriggerId(5L);
        record.setPlantCode("SZ");
        record.setPlantName("深圳");
        record.setFaiNo("FAI-TEST");
        record.setMaterialCode("M1");
        record.setInspectionResult("合格");
        record.setSignatureStatus("已签");

        FaiInspectionItem item = new FaiInspectionItem();
        item.setFaiRecordId(1L);
        item.setParamCode("P1");
        item.setActualValue(new BigDecimal("10"));
        item.setUpperLimit(new BigDecimal("5")); // 10 > 5 → 不合格
        item.setLowerLimit(null);
        item.setStandardValue(null);

        FaiChangeTrigger trigger = new FaiChangeTrigger();
        trigger.setId(5L);

        when(recordMapper.selectById(any(Long.class))).thenReturn(record);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(signatureMapper.selectList(any())).thenReturn(List.of());
        when(standardMapper.selectList(any())).thenReturn(List.of());
        when(standardItemMapper.selectList(any())).thenReturn(List.of());
        when(changeTriggerMapper.selectById(any(Long.class))).thenReturn(trigger);

        FaiInspectionServiceImpl service = new FaiInspectionServiceImpl(
                recordMapper,
                itemMapper,
                changeTriggerMapper,
                standardMapper,
                standardItemMapper,
                signatureMapper,
                mock(FaiStandardService.class),
                mock(SpcSubgroupService.class),
                exceptionService,
                userMapper,
                mock(AuditLogService.class));

        LoginUser loginUser = LoginUser.builder().userId(9L).realName("测试员").build();
        service.judge(1L, loginUser);

        // L16：不合格回退使电子签名失效
        ArgumentCaptor<FaiInspectionRecord> captor = ArgumentCaptor.forClass(FaiInspectionRecord.class);
        verify(recordMapper).updateById(captor.capture());
        assertEquals("未签", captor.getValue().getSignatureStatus());

        // L30：自动建异常整改单
        verify(exceptionService).createFromFai(record, loginUser);
    }

    private FaiInspectionServiceImpl newService(Object... mocks) {
        return new FaiInspectionServiceImpl(
                (FaiInspectionRecordMapper) mocks[0],
                (FaiInspectionItemMapper) mocks[1],
                mock(FaiChangeTriggerMapper.class),
                mock(FaiInspectionStandardMapper.class),
                mock(FaiInspectionStandardItemMapper.class),
                (FaiSignatureMapper) mocks[2],
                mock(FaiStandardService.class),
                mock(SpcSubgroupService.class),
                mock(ExceptionService.class),
                mock(SysUserMapper.class),
                mock(AuditLogService.class));
    }

    /**
     * 风险一（绑定）+ 风险二（双校验）：签名后逐项内容被篡改，
     * verifySignatureIntegrity 应返回 TAMPERED，且 report() 应抛出 FORBIDDEN 拒绝导出。
     */
    @Test
    void signatureTampering_afterSigned_detectsTamperAndRejectsReport() {
        FaiInspectionRecordMapper recordMapper = mock(FaiInspectionRecordMapper.class);
        FaiInspectionItemMapper itemMapper = mock(FaiInspectionItemMapper.class);
        FaiSignatureMapper signatureMapper = mock(FaiSignatureMapper.class);

        FaiInspectionRecord record = new FaiInspectionRecord();
        record.setId(1L);
        record.setFaiNo("FAI-T");
        record.setInspectionResult("合格");
        record.setSignatureStatus("已签");

        FaiInspectionItem item = new FaiInspectionItem();
        item.setFaiRecordId(1L);
        item.setParamCode("P1");
        item.setActualValue(new BigDecimal("10"));
        item.setResult("合格");
        item.setStandardValue("10");
        item.setUpperLimit(new BigDecimal("20"));
        item.setLowerLimit(new BigDecimal("0"));

        FaiSignature sig = new FaiSignature();
        sig.setSignerId("9");
        sig.setSignReason("检验完成");
        LocalDateTime signedAt = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        sig.setSignedAt(signedAt);

        FaiInspectionServiceImpl service = newService(recordMapper, itemMapper, signatureMapper);
        // 预置正确的内容绑定哈希（使用与生产一致的算法）
        sig.setContentHash(service.computeContentHash(record, List.of(item), "9",
                signedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), "检验完成"));

        when(recordMapper.selectById(1L)).thenReturn(record);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(signatureMapper.selectList(any())).thenReturn(List.of(sig));

        // 未篡改：完整性 INTACT
        assertEquals(SignatureIntegrity.INTACT, service.verifySignatureIntegrity(1L));

        // 篡改：实际值被改，重算哈希失配 → TAMPERED
        item.setActualValue(new BigDecimal("999"));
        assertEquals(SignatureIntegrity.TAMPERED, service.verifySignatureIntegrity(1L));

        // 状态 + 哈希双校验：篡改后 report() 拒绝出报告
        BusinessException ex = assertThrows(BusinessException.class, () -> service.report(1L));
        assertTrue(ex.getMessage().contains("完整性校验"));
    }

    /**
     * 风险二（祖父条款）：历史遗留签名 content_hash 为空，verifySignatureIntegrity 返回
     * LEGACY_UNVERIFIABLE，report() 仍允许导出但标记 legacySignature=true。
     */
    @Test
    void legacySignature_withoutContentHash_allowsReportWithFlag() {
        FaiInspectionRecordMapper recordMapper = mock(FaiInspectionRecordMapper.class);
        FaiInspectionItemMapper itemMapper = mock(FaiInspectionItemMapper.class);
        FaiSignatureMapper signatureMapper = mock(FaiSignatureMapper.class);

        FaiInspectionRecord record = new FaiInspectionRecord();
        record.setId(2L);
        record.setFaiNo("FAI-L");
        record.setInspectionResult("合格");
        record.setSignatureStatus("已签");

        FaiSignature sig = new FaiSignature();
        sig.setSignerId("9");
        sig.setSignReason("历史签");
        sig.setSignedAt(LocalDateTime.of(2025, 1, 1, 0, 0, 0));
        sig.setContentHash(null); // 历史遗留，无内容绑定哈希

        when(recordMapper.selectById(2L)).thenReturn(record);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(signatureMapper.selectList(any())).thenReturn(List.of(sig));

        FaiInspectionServiceImpl service = newService(recordMapper, itemMapper, signatureMapper);
        assertEquals(SignatureIntegrity.LEGACY_UNVERIFIABLE, service.verifySignatureIntegrity(2L));

        FaiReportResponse resp = service.report(2L);
        assertTrue(resp.getLegacySignature());
        assertFalse(resp.getSignatureIntact());
    }

    /**
     * 风险一（未签名不进档案）：archiveOnly 模式应在服务端强制
     * signature_status='已签'，但不强制 inspection_result（不合格亦可进档案）。
     */
    @Test
    void archiveOnly_filtersUnsignedAndPending() {
        FaiInspectionRecordMapper recordMapper = mock(FaiInspectionRecordMapper.class);
        when(recordMapper.selectPage(any(), any())).thenReturn(new Page<>());

        FaiInspectionServiceImpl service = newService(recordMapper,
                mock(FaiInspectionItemMapper.class), mock(FaiSignatureMapper.class));

        FaiInspectionQuery query = new FaiInspectionQuery();
        query.setArchiveOnly(true);
        service.page(query, "PLANT");

        ArgumentCaptor<LambdaQueryWrapper<FaiInspectionRecord>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recordMapper).selectPage(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("signature_status"), "档案模式应强制已签条件（未签不进档案）");
        assertFalse(sql.contains("inspection_result"), "档案模式不应强制合格过滤（不合格亦可进档案）");
    }
}
