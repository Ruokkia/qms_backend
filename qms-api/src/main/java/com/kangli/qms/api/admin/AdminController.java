package com.kangli.qms.api.admin;

import com.kangli.qms.common.R;
import com.kangli.qms.service.admin.dto.AdminActionRequest;
import com.kangli.qms.service.admin.dto.AdminUserRequest;
import com.kangli.qms.service.admin.dto.RolePermissionRequest;
import com.kangli.qms.service.admin.dto.RoleCreateRequest;
import com.kangli.qms.service.admin.AdminService;
import com.kangli.qms.domain.admin.vo.AdminUserVO;
import com.kangli.qms.domain.admin.vo.RolePermissionVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import com.kangli.qms.domain.admin.entity.AuditLog;

@RestController
@RequestMapping("/api/v1/admin")
@Api(tags = "系统管理")
@Validated
public class AdminController {
    private final AdminService adminService;
    public AdminController(AdminService adminService) { this.adminService = adminService; }
    @GetMapping("/users") @ApiOperation("账号列表")
    public R<List<AdminUserVO>> users() { return R.ok(adminService.listUsers()); }
    @PostMapping("/users") @ApiOperation("创建账号")
    public R<AdminUserVO> create(@Valid @RequestBody AdminUserRequest request, HttpServletRequest servletRequest) { return R.ok(adminService.createUser(request, servletRequest.getRemoteAddr())); }
    @PutMapping("/users/{id}") @ApiOperation("编辑账号")
    public R<AdminUserVO> update(@PathVariable Long id, @Valid @RequestBody AdminUserRequest request, HttpServletRequest servletRequest) { return R.ok(adminService.updateUser(id, request, servletRequest.getRemoteAddr())); }
    @PostMapping("/users/{id}/enable") @ApiOperation("启用账号")
    public R<Void> enable(@PathVariable Long id, @Valid @RequestBody AdminActionRequest request, HttpServletRequest servletRequest) { adminService.setUserStatus(id, true, request, servletRequest.getRemoteAddr()); return R.ok(); }
    @PostMapping("/users/{id}/disable") @ApiOperation("停用账号")
    public R<Void> disable(@PathVariable Long id, @Valid @RequestBody AdminActionRequest request, HttpServletRequest servletRequest) { adminService.setUserStatus(id, false, request, servletRequest.getRemoteAddr()); return R.ok(); }
    @PostMapping("/users/{id}/unlock") @ApiOperation("解锁账号")
    public R<Void> unlock(@PathVariable Long id, @Valid @RequestBody AdminActionRequest request, HttpServletRequest servletRequest) { adminService.unlockUser(id, request, servletRequest.getRemoteAddr()); return R.ok(); }
    @PostMapping("/users/{id}/reset-password") @ApiOperation("重置密码")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody AdminActionRequest request, HttpServletRequest servletRequest) { adminService.resetPassword(id, request, servletRequest.getRemoteAddr()); return R.ok(); }
    @GetMapping("/roles") @ApiOperation("角色列表")
    public R<List<RolePermissionVO>> roles() { return R.ok(adminService.listRoles()); }
    @GetMapping("/roles/{roleCode}/permissions") @ApiOperation("角色权限")
    public R<RolePermissionVO> permissions(@PathVariable String roleCode) { return R.ok(adminService.getRolePermissions(roleCode)); }
    @PutMapping("/roles/{roleCode}/permissions") @ApiOperation("更新角色权限")
    public R<RolePermissionVO> updatePermissions(@PathVariable String roleCode, @Valid @RequestBody RolePermissionRequest request, HttpServletRequest servletRequest) { return R.ok(adminService.updateRolePermissions(roleCode, request, servletRequest.getRemoteAddr())); }
    @PostMapping("/roles") @ApiOperation("新建角色")
    public R<RolePermissionVO> createRole(@Valid @RequestBody RoleCreateRequest request, HttpServletRequest servletRequest) { return R.ok(adminService.createRole(request, servletRequest.getRemoteAddr())); }
    @DeleteMapping("/roles/{roleCode}") @ApiOperation("删除角色")
    public R<Void> deleteRole(@PathVariable String roleCode, @Valid @RequestBody AdminActionRequest request, HttpServletRequest servletRequest) { adminService.deleteRole(roleCode, request, servletRequest.getRemoteAddr()); return R.ok(); }
    @GetMapping("/audit") @ApiOperation("管理审计日志")
    public R<List<AuditLog>> audit() { return R.ok(adminService.listAuditLogs()); }
}
