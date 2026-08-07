package com.kangli.qms.service;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.admin.entity.SysRole;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.domain.admin.mapper.SysRoleMapper;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.notification.entity.NotificationConfig;
import com.kangli.qms.domain.notification.mapper.NotificationConfigMapper;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.service.notification.impl.NotificationConfigServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationConfigServiceImplTest {

    @AfterEach
    void clearLoginUser() {
        LoginUserHolder.clear();
    }

    @Test
    void updateWritesAuditLogForCurrentPlant() {
        NotificationConfigMapper configMapper = mock(NotificationConfigMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        NotificationConfigServiceImpl service = new NotificationConfigServiceImpl(configMapper, auditLogMapper, roleMapper, sysUserMapper);
        NotificationConfig existing = new NotificationConfig();
        existing.setId(1L);
        existing.setVersion(1);
        existing.setRoleCodes("[\"R02\"]");
        existing.setEnabled(1);
        when(configMapper.selectById(1L)).thenReturn(existing);
        when(configMapper.updateById(any(NotificationConfig.class))).thenReturn(1);
        LoginUserHolder.set(LoginUser.builder().userId(13L).account("qms_admin").realName("管理员").plantCode(PlantCode.SZ).build());

        service.update(1L, existing, "管理员", "127.0.0.1", "停用通知");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(auditCaptor.capture());
        assertEquals("SZ", auditCaptor.getValue().getPlantCode());
    }

    @Test
    void updateWritesReadableNotificationChangeSummary() {
        NotificationConfigMapper configMapper = mock(NotificationConfigMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        NotificationConfigServiceImpl service = new NotificationConfigServiceImpl(configMapper, auditLogMapper, roleMapper, sysUserMapper);
        NotificationConfig existing = new NotificationConfig();
        existing.setId(1L);
        existing.setScenarioName("异常创建");
        existing.setRoleCodes("[\"R02\"]");
        existing.setSeverityExtraRoles("[]");
        existing.setEnabled(1);
        NotificationConfig request = new NotificationConfig();
        request.setRoleCodes("[\"R04\"]");
        request.setSeverityExtraRoles("[]");
        request.setEnabled(0);
        SysRole inspector = new SysRole(); inspector.setRoleCode("R02"); inspector.setRoleName("检验员");
        SysRole engineer = new SysRole(); engineer.setRoleCode("R04"); engineer.setRoleName("质量工程师");
        when(configMapper.selectById(1L)).thenReturn(existing);
        when(configMapper.updateById(any(NotificationConfig.class))).thenReturn(1);
        when(roleMapper.selectList(any())).thenReturn(java.util.List.of(inspector, engineer));

        service.update(1L, request, "管理员", "127.0.0.1", "调整接收范围");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(auditCaptor.capture());
        String summary = auditCaptor.getValue().getOperationContent();
        assertTrue(summary.contains("通知配置（异常创建）"));
        assertTrue(summary.contains("接收角色由“检验员”调整为“质量工程师”"));
        assertTrue(summary.contains("状态由“启用”调整为“停用”"));
    }
}
