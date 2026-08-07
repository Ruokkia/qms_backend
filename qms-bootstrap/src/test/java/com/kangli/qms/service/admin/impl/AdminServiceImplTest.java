package com.kangli.qms.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.service.admin.dto.RolePermissionRequest;
import com.kangli.qms.service.admin.dto.RoleCreateRequest;
import com.kangli.qms.service.admin.dto.AdminActionRequest;
import com.kangli.qms.service.admin.dto.AdminUserRequest;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.admin.entity.SysRole;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.domain.admin.mapper.SysRoleMapper;
import com.kangli.qms.domain.admin.mapper.SysRolePermissionMapper;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminServiceImplTest {

    @Test
    void listAuditLogs_pagesSystemAndNotificationConfigAudits() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper permissionMapper = mock(SysRolePermissionMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        AdminServiceImpl service = new AdminServiceImpl(userMapper, roleMapper, permissionMapper, auditLogMapper, mock(RedisUtil.class));
        AuditLog notificationAudit = new AuditLog();
        notificationAudit.setTableName("notification_config");
        notificationAudit.setOperationType("UPDATE_NOTIFICATION_CONFIG");
        notificationAudit.setAfterData("{}");
        when(auditLogMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<AuditLog> page = invocation.getArgument(0);
            page.setRecords(List.of(notificationAudit));
            page.setTotal(1L);
            return page;
        });

        PageResult<AuditLog> result = service.listAuditLogs(1, 10);

        assertEquals(1L, result.getTotal());
        assertEquals("notification_config", result.getList().get(0).getTableName());
        assertEquals("通知配置已更新（历史记录）", result.getList().get(0).getOperationContent());
    }

    @Test
    void createUser_shouldUseDefaultPasswordWhenPasswordIsBlank() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper permissionMapper = mock(SysRolePermissionMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        AdminServiceImpl service = new AdminServiceImpl(userMapper, roleMapper, permissionMapper, auditLogMapper, redisUtil);
        AtomicReference<SysUser> insertedUser = new AtomicReference<>();
        SysRole role = new SysRole();
        role.setRoleCode("R01");
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(role);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(501L);
            insertedUser.set(user);
            return 1;
        });

        AdminUserRequest request = new AdminUserRequest();
        request.setAccount("default_pwd_user");
        request.setRealName("默认密码测试");
        request.setRoleCode("R01");
        request.setPlantCode("SZ");
        request.setPassword("   ");

        service.createUser(request, "127.0.0.1");

        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches("123456", insertedUser.get().getPasswordHash()));
    }

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
        assertEquals("角色“操作员”的权限已调整", auditCaptor.getValue().getOperationContent());
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

    @Test
    void updateRolePermissions_shouldRejectSuperAdminRole() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper permissionMapper = mock(SysRolePermissionMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        AdminServiceImpl service = new AdminServiceImpl(userMapper, roleMapper, permissionMapper, auditLogMapper, redisUtil);

        RolePermissionRequest request = new RolePermissionRequest();
        request.setDataScope("OWN_PLANT");
        request.setPermissions(Collections.emptyList());
        request.setReason("测试锁定超级管理员权限");
        request.setVersion(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateRolePermissions("R00", request, "127.0.0.1"));
        assertEquals("超级管理员权限已锁定，不能修改", exception.getMessage());
        verify(roleMapper, never()).selectOne(any(Wrapper.class));
        verify(roleMapper, never()).updateById(any(SysRole.class));
    }

    @Test
    void createRole_shouldGenerateNextNeverReusedRoleCode() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper permissionMapper = mock(SysRolePermissionMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        AdminServiceImpl service = new AdminServiceImpl(userMapper, roleMapper, permissionMapper, auditLogMapper, redisUtil);
        AtomicReference<SysRole> insertedRole = new AtomicReference<>();

        when(roleMapper.selectMaxRoleNumberIncludingDeleted()).thenReturn(12);
        when(roleMapper.insert(any(SysRole.class))).thenAnswer(invocation -> {
            SysRole role = invocation.getArgument(0);
            role.setId(201L);
            insertedRole.set(role);
            return 1;
        });
        when(roleMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> insertedRole.get());
        when(permissionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleName("自定义审核员");
        request.setDataScope("OWN_PLANT");
        request.setPermissions(List.of("fai:VIEW"));
        request.setReason("新增审核岗位");

        service.createRole(request, "127.0.0.1");

        verify(roleMapper).lockRoleCodeGeneration();
        assertEquals("R13", insertedRole.get().getRoleCode());
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(auditCaptor.capture());
        assertEquals("CREATE_ROLE", auditCaptor.getValue().getOperationType());
    }

    @Test
    void createRole_shouldPopulateRequiredAuditTimestamps() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper permissionMapper = mock(SysRolePermissionMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        AdminServiceImpl service = new AdminServiceImpl(userMapper, roleMapper, permissionMapper, auditLogMapper, redisUtil);
        AtomicReference<SysRole> insertedRole = new AtomicReference<>();

        when(roleMapper.selectMaxRoleNumberIncludingDeleted()).thenReturn(7);
        when(roleMapper.insert(any(SysRole.class))).thenAnswer(invocation -> {
            SysRole role = invocation.getArgument(0);
            role.setId(202L);
            insertedRole.set(role);
            return 1;
        });
        when(roleMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> insertedRole.get());
        when(permissionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleName("审计测试角色");
        request.setDataScope("OWN_PLANT");
        request.setPermissions(List.of("fai:VIEW"));
        request.setReason("验证创建时间");

        service.createRole(request, "127.0.0.1");

        assertNotNull(insertedRole.get().getCreatedAt());
        assertNotNull(insertedRole.get().getUpdatedAt());
    }

    @Test
    void deleteRole_shouldRejectRoleBoundToEnabledUsers() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper permissionMapper = mock(SysRolePermissionMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        AdminServiceImpl service = new AdminServiceImpl(userMapper, roleMapper, permissionMapper, auditLogMapper, redisUtil);
        SysRole role = new SysRole();
        role.setId(301L);
        role.setRoleCode("R08");
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(role);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        AdminActionRequest request = new AdminActionRequest();
        request.setReason("清理角色");

        assertThrows(BusinessException.class, () -> service.deleteRole("R08", request, "127.0.0.1"));

        verify(roleMapper, never()).deleteById(anyLong());
        verify(permissionMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void deleteRole_shouldAllowNonSuperAdminBuiltInRoleWhenNoEnabledUsersRemain() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper permissionMapper = mock(SysRolePermissionMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        AdminServiceImpl service = new AdminServiceImpl(userMapper, roleMapper, permissionMapper, auditLogMapper, redisUtil);
        SysRole role = new SysRole();
        role.setId(302L);
        role.setRoleCode("R01");
        role.setRoleName("操作工");
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(role);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        AdminActionRequest request = new AdminActionRequest();
        request.setReason("撤销旧角色");

        service.deleteRole("R01", request, "127.0.0.1");

        verify(roleMapper).deleteById(302L);
        verify(permissionMapper).delete(any(Wrapper.class));
    }

    @Test
    void enableUser_shouldRejectDeletedRoleUntilAccountRoleIsChanged() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper permissionMapper = mock(SysRolePermissionMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        AdminServiceImpl service = new AdminServiceImpl(userMapper, roleMapper, permissionMapper, auditLogMapper, redisUtil);
        SysUser user = new SysUser();
        user.setId(401L);
        user.setRoleCode("R08");
        user.setStatus((short) 0);
        when(userMapper.selectById(401L)).thenReturn(user);
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        AdminActionRequest request = new AdminActionRequest();
        request.setReason("重新启用");

        assertThrows(BusinessException.class, () -> service.setUserStatus(401L, true, request, "127.0.0.1"));

        verify(userMapper, never()).updateById(any(SysUser.class));
    }
}
