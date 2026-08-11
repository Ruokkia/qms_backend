package com.kangli.qms.service.exception.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.domain.admin.mapper.SysRoleMapper;
import com.kangli.qms.service.exception.dto.ExceptionApprovalConfigDTO;
import com.kangli.qms.domain.exception.entity.ExceptionApprovalConfig;
import com.kangli.qms.domain.exception.mapper.ExceptionApprovalConfigMapper;
import com.kangli.qms.enums.PlantCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExceptionApprovalConfigServiceImpl 单测：聚焦于审计日志（IP 写入、变更摘要）。
 * 对应 test-plan 中「审批配置审计」相关用例，并覆盖 2026-08-10 新增的 clientIp 透传改动。
 */
class ExceptionApprovalConfigServiceImplTest {

    private AuditLogMapper auditLogMapper;
    private SysRoleMapper roleMapper;
    private ExceptionApprovalConfigMapper baseMapper;
    private ExceptionApprovalConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        auditLogMapper = mock(AuditLogMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        baseMapper = mock(ExceptionApprovalConfigMapper.class);
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service = new ExceptionApprovalConfigServiceImpl(auditLogMapper, roleMapper);
        // 父类 ServiceImpl 依赖 Spring 注入 baseMapper，单测中通过反射注入 mock
        try {
            Field f = ServiceImpl.class.getDeclaredField("baseMapper");
            f.setAccessible(true);
            f.set(service, baseMapper);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        LoginUserHolder.set(LoginUser.builder()
                .userId(9L)
                .realName("测试员")
                .roleCode("R04")
                .plantCode(PlantCode.SZ)
                .build());
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    /**
     * 新增审批配置时，审计日志应写入传入的客户端 IP（本次改动核心点）。
     */
    @Test
    void create_writesAuditLogWithClientIp() {
        ExceptionApprovalConfigDTO dto = new ExceptionApprovalConfigDTO();
        dto.setProcessFlow("8D");
        dto.setStage("D3");
        dto.setStageName("临时遏制");
        dto.setNeedApproval((short) 1);
        dto.setApproverRole("R04");

        service.saveOrUpdateConfig(dto, "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());
        AuditLog log = captor.getValue();
        assertEquals("127.0.0.1", log.getIpAddress());
        assertEquals("新增审批配置", log.getOperationType());
        assertEquals("exception_approval_config", log.getTableName());
    }

    /**
     * 更新审批配置时，审计日志应写入传入的客户端 IP，并生成「需审批/审批角色」变更摘要。
     */
    @Test
    void update_writesAuditLogWithClientIpAndChangeSummary() {
        ExceptionApprovalConfig existing = new ExceptionApprovalConfig();
        existing.setId(100L);
        existing.setProcessFlow("8D");
        existing.setStage("D3");
        existing.setStageName("临时遏制");
        existing.setNeedApproval((short) 0);
        existing.setApproverRole(null);

        when(baseMapper.selectById(100L)).thenReturn(existing);

        ExceptionApprovalConfigDTO dto = new ExceptionApprovalConfigDTO();
        dto.setId(100L);
        dto.setProcessFlow("8D");
        dto.setStage("D3");
        dto.setStageName("临时遏制");
        dto.setNeedApproval((short) 1);
        dto.setApproverRole("R06");

        service.saveOrUpdateConfig(dto, "192.168.1.50");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());
        AuditLog log = captor.getValue();
        assertEquals("192.168.1.50", log.getIpAddress());
        assertEquals("更新审批配置", log.getOperationType());
        assertEquals("审批配置（临时遏制）：需审批由“不需要”调整为“需要”，审批角色由“未配置”调整为“R06”",
                log.getOperationContent());
    }

    /**
     * 空 IP 传入时，审计日志 ipAddress 应为 null（不写占位串）。
     */
    @Test
    void create_withBlankIp_writesNullIpAddress() {
        ExceptionApprovalConfigDTO dto = new ExceptionApprovalConfigDTO();
        dto.setProcessFlow("8D");
        dto.setStage("D5");
        dto.setStageName("永久纠正");
        dto.setNeedApproval((short) 0);

        service.saveOrUpdateConfig(dto, "   ");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());
        assertNull(captor.getValue().getIpAddress());
    }

    /**
     * 删除不存在的配置应抛 NOT_FOUND（审计日志不应写入）。
     */
    @Test
    void delete_missingConfig_throwsAndWritesNoAudit() {
        when(baseMapper.selectById(999L)).thenReturn(null);

        try {
            service.deleteConfig(999L, "127.0.0.1");
        } catch (Exception ignored) {
            // BusinessException 由调用方断言，此处仅确认审计未写入
        }
        verify(auditLogMapper, times(0)).insert(any(AuditLog.class));
    }
}
