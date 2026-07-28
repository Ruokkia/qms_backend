package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.kangli.qms.dto.RolePermissionRequest;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.entity.AuditLog;
import com.kangli.qms.entity.SysRole;
import com.kangli.qms.mapper.AuditLogMapper;
import com.kangli.qms.mapper.SysRoleMapper;
import com.kangli.qms.mapper.SysRolePermissionMapper;
import com.kangli.qms.mapper.SysUserMapper;
import com.kangli.qms.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminServiceImplTest {

    @Test
    void updateRolePermissions_shouldAuditWithRoleDatabaseId() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper permissionMapper = mock(SysRolePermissionMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        AdminServiceImpl service = new AdminServiceImpl(userMapper, roleMapper, permissionMapper, auditLogMapper, redisUtil);

        SysRole role = new SysRole();
        role.setId(101L);
        role.setRoleCode("R01");
        role.setRoleName("操作员");
        role.setDataScope("OWN_PLANT");
        role.setVersion(1);
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(role);
        when(roleMapper.updateById(any(SysRole.class))).thenReturn(1);
        when(permissionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        RolePermissionRequest request = new RolePermissionRequest();
        request.setDataScope("OWN_PLANT");
        request.setPermissions(Collections.emptyList());
        request.setReason("测试角色权限审计");
        request.setVersion(1);

        service.updateRolePermissions("R01", request, "127.0.0.1");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(auditCaptor.capture());
        assertEquals(101L, auditCaptor.getValue().getRecordId());
        assertEquals("UPDATE_ROLE_PERMISSION", auditCaptor.getValue().getOperationType());
    }

    @Test
    void updateRolePermissions_shouldRejectStaleVersion() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper permissionMapper = mock(SysRolePermissionMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        AdminServiceImpl service = new AdminServiceImpl(userMapper, roleMapper, permissionMapper, auditLogMapper, redisUtil);

        SysRole role = new SysRole();
        role.setRoleCode("R01");
        role.setDataScope("OWN_PLANT");
        role.setVersion(2);
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(role);

        RolePermissionRequest request = new RolePermissionRequest();
        request.setDataScope("OWN_PLANT");
        request.setPermissions(Collections.emptyList());
        request.setReason("并发测试");
        request.setVersion(1);

        assertThrows(BusinessException.class,
                () -> service.updateRolePermissions("R01", request, "127.0.0.1"));
        verify(roleMapper, never()).updateById(any(SysRole.class));
    }
}
