package com.kangli.qms.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.admin.entity.SysRole;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.domain.admin.mapper.SysRoleMapper;
import com.kangli.qms.domain.admin.mapper.SysRolePermissionMapper;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.admin.vo.RolePermissionVO;
import com.kangli.qms.service.admin.dto.RolePermissionRequest;
import com.kangli.qms.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminServiceImpl 单测：聚焦角色权限管理的安全约束与审计。
 */
class AdminServiceImplTest {

    private SysUserMapper userMapper;
    private SysRoleMapper roleMapper;
    private SysRolePermissionMapper permissionMapper;
    private AuditLogMapper auditLogMapper;
    private RedisUtil redisUtil;
    private AdminServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        permissionMapper = mock(SysRolePermissionMapper.class);
        auditLogMapper = mock(AuditLogMapper.class);
        redisUtil = mock(RedisUtil.class);
        service = new AdminServiceImpl(userMapper, roleMapper, permissionMapper, auditLogMapper, redisUtil);

        LoginUserHolder.set(LoginUser.builder().userId(1L).account("admin").roleCode("R06").build());
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private SysRole role(String code, int version) {
        SysRole r = new SysRole();
        r.setId(10L);
        r.setRoleCode(code);
        r.setRoleName("角色" + code);
        r.setDataScope("OWN_PLANT");
        r.setVersion(version);
        return r;
    }

    // ===== ADMIN-007 超管角色权限锁定，禁止修改 =====

    @Test
    @DisplayName("R00 超级管理员角色权限禁止修改")
    void updateRolePermissions_R00_rejected() {
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(role("R00", 1));

        RolePermissionRequest req = new RolePermissionRequest();
        req.setDataScope("OWN_PLANT");
        req.setVersion(1);
        req.setPermissions(List.of("trace:VIEW"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateRolePermissions("R00", req, "127.0.0.1"));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("超级管理员"));
    }

    // ===== ADMIN 非系统管理员角色不能授予 systemAdmin 权限 =====

    // ===== ADMIN-010 删除角色前仍有启用账号被拒 =====

    @Test
    @DisplayName("角色仍绑定启用账号时删除被拒")
    void deleteRole_withEnabledUsers_rejected() {
        SysRole r = role("R07", 1);
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(r);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteRole("R07", req(), "127.0.0.1"));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("使用中的账号"));
    }

    // ===== ADMIN-010 内置 R00 角色禁止删除 =====

    @Test
    @DisplayName("内置超级管理员角色禁止删除")
    void deleteRole_R00_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteRole("R00", req(), "127.0.0.1"));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("不能删除"));
    }

    // ===== ADMIN-005 更新角色权限成功写入审计 =====

    @Test
    @DisplayName("更新角色权限成功后写入审计日志")
    void updateRolePermissions_success_writesAudit() {
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(role("R03", 1));
        when(roleMapper.updateById(any(SysRole.class))).thenReturn(1);
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        RolePermissionRequest req = new RolePermissionRequest();
        req.setDataScope("ALL_PLANTS");
        req.setVersion(1);
        req.setPermissions(List.of("exception:VIEW", "exception:EDIT"));

        RolePermissionVO vo = service.updateRolePermissions("R03", req, "192.168.1.10");

        assertEquals("R03", vo.getRoleCode());
        var captor = forClass(AuditLog.class);
        verify(auditLogMapper, org.mockito.Mockito.times(1)).insert(captor.capture());
        AuditLog log = captor.getValue();
        assertEquals("UPDATE_ROLE_PERMISSION", log.getOperationType());
        assertEquals("192.168.1.10", log.getIpAddress());
        assertEquals("角色“角色R03”的权限已调整", log.getOperationContent());
    }

    // ===== ADMIN 数据范围无效被拒 =====

    @Test
    @DisplayName("无效数据范围被拒")
    void updateRolePermissions_invalidDataScope_rejected() {
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(role("R03", 1));

        RolePermissionRequest req = new RolePermissionRequest();
        req.setDataScope("INVALID");
        req.setVersion(1);
        req.setPermissions(List.of("exception:VIEW"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateRolePermissions("R03", req, "127.0.0.1"));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    private com.kangli.qms.service.admin.dto.AdminActionRequest req() {
        com.kangli.qms.service.admin.dto.AdminActionRequest r = new com.kangli.qms.service.admin.dto.AdminActionRequest();
        r.setReason("测试");
        return r;
    }
}
