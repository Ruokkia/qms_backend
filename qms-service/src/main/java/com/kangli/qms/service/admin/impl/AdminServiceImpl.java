package com.kangli.qms.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.AuthConstants;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.admin.dto.AdminActionRequest;
import com.kangli.qms.service.admin.dto.AdminUserRequest;
import com.kangli.qms.service.admin.dto.RoleCreateRequest;
import com.kangli.qms.service.admin.dto.RolePermissionRequest;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.admin.entity.SysRole;
import com.kangli.qms.domain.admin.entity.SysRolePermission;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.domain.admin.mapper.SysRoleMapper;
import com.kangli.qms.domain.admin.mapper.SysRolePermissionMapper;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.security.PermissionService;
import com.kangli.qms.service.admin.AdminService;
import com.kangli.qms.util.RedisUtil;
import com.kangli.qms.domain.admin.vo.AdminUserVO;
import com.kangli.qms.domain.admin.vo.RolePermissionVO;
import com.kangli.qms.domain.admin.vo.PermissionDisplayVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ALL_PLANTS_KEY = "ALL_PLANTS";
    private static final String OWN_PLANT_KEY = "OWN_PLANT";
    private static final List<String> SYSTEM_ADMIN_ROLES = Arrays.asList("R00", "R06");
    private static final List<String> SYSTEM_ADMIN_PERMISSIONS = Arrays.asList(
            "systemAdmin:VIEW", "systemAdmin:EDIT", "systemAdmin:APPROVE", "systemAdmin:EXPORT");
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper permissionMapper;
    private final AuditLogMapper auditLogMapper;
    private final RedisUtil redisUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminServiceImpl(SysUserMapper userMapper, SysRoleMapper roleMapper, SysRolePermissionMapper permissionMapper,
                            AuditLogMapper auditLogMapper, RedisUtil redisUtil) {
        this.userMapper = userMapper; this.roleMapper = roleMapper; this.permissionMapper = permissionMapper;
        this.auditLogMapper = auditLogMapper; this.redisUtil = redisUtil;
    }

    @Override public List<AdminUserVO> listUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getAccount)).stream().map(this::toUserVO).collect(Collectors.toList());
    }

    @Override @Transactional public AdminUserVO createUser(AdminUserRequest request, String ip) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getAccount, request.getAccount().trim())) > 0) throw new BusinessException(ResultCode.BAD_REQUEST, "账号已存在");
        validateRoleAndPlant(request.getRoleCode(), request.getPlantCode());
        String initialPassword = request.getPassword() == null || request.getPassword().trim().isEmpty()
                ? "123456" : request.getPassword().trim();
        if (initialPassword.length() < 6) throw new BusinessException(ResultCode.BAD_REQUEST, "初始密码至少 6 位");
        SysUser user = new SysUser();
        user.setAccount(request.getAccount().trim()); user.setRealName(request.getRealName().trim()); user.setRoleCode(request.getRoleCode());
        user.setPlantCode(request.getPlantCode()); user.setPlantName(plantName(request.getPlantCode(), request.getPlantName()));
        user.setPasswordHash(passwordEncoder.encode(initialPassword)); user.setStatus((short) 1); user.setAuthVersion(1);
        userMapper.insert(user); audit("CREATE_USER", user.getId(), request.getReason(), ip, user.getAccount()); return toUserVO(user);
    }

    @Override @Transactional public AdminUserVO updateUser(Long id, AdminUserRequest request, String ip) {
        SysUser user = requireUser(id); protectSelfRoleChange(user, request.getRoleCode()); validateRoleAndPlant(request.getRoleCode(), request.getPlantCode());
        String before = user.getRoleCode() + "/" + user.getPlantCode();
        user.setRealName(request.getRealName().trim()); user.setRoleCode(request.getRoleCode()); user.setPlantCode(request.getPlantCode()); user.setPlantName(plantName(request.getPlantCode(), request.getPlantName()));
        invalidate(user); userMapper.updateById(user); audit("UPDATE_USER", id, request.getReason(), ip, before + " -> " + user.getRoleCode() + "/" + user.getPlantCode()); return toUserVO(user);
    }

    @Override @Transactional public void setUserStatus(Long id, boolean enabled, AdminActionRequest request, String ip) {
        SysUser user = requireUser(id); if (!enabled) { protectLastSuperAdmin(user); } protectSelfDisable(user);
        if (enabled) requireRole(user.getRoleCode());
        user.setStatus(enabled ? (short) 1 : (short) 0); invalidate(user); userMapper.updateById(user); audit(enabled ? "ENABLE_USER" : "DISABLE_USER", id, request.getReason(), ip, user.getAccount());
    }

    @Override @Transactional public void unlockUser(Long id, AdminActionRequest request, String ip) {
        SysUser user = requireUser(id); redisUtil.delete(AuthConstants.lockKey(user.getAccount())); redisUtil.delete(AuthConstants.failCountKey(user.getAccount()));
        user.setLoginFailCount(0); userMapper.update(user, new UpdateWrapper<SysUser>().eq("id", user.getId()).setSql("locked_until = null")); audit("UNLOCK_USER", id, request.getReason(), ip, user.getAccount());
    }

    @Override @Transactional public void resetPassword(Long id, AdminActionRequest request, String ip) {
        SysUser user = requireUser(id); if (request.getPassword() == null || request.getPassword().trim().length() < 6) throw new BusinessException(ResultCode.BAD_REQUEST, "新密码至少 6 位");
        user.setPasswordHash(passwordEncoder.encode(request.getPassword())); invalidate(user); userMapper.updateById(user); audit("RESET_PASSWORD", id, request.getReason(), ip, user.getAccount());
    }

    @Override public List<RolePermissionVO> listRoles() { return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getRoleCode)).stream().map(role -> getRolePermissions(role.getRoleCode())).collect(Collectors.toList()); }

    @Override @Transactional public RolePermissionVO createRole(RoleCreateRequest request, String ip) {
        validateDataScope(request.getDataScope());
        if (request.getPermissions() == null || request.getPermissions().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请至少选择一个模块权限");
        }
        roleMapper.lockRoleCodeGeneration();
        int nextRoleNumber = roleMapper.selectMaxRoleNumberIncludingDeleted() + 1;
        String roleCode = String.format("R%02d", nextRoleNumber);
        List<String> permissions = normalizeSystemAdminPermissions(roleCode, request.getPermissions());

        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName(request.getRoleName().trim());
        role.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        role.setStatus((short) 1);
        role.setPlantCode("*");
        role.setPlantName("全局");
        role.setDataScope(request.getDataScope());
        role.setVersion(1);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        roleMapper.insert(role);
        replacePermissions(roleCode, permissions);
        audit("CREATE_ROLE", role.getId(), request.getReason(), ip, roleCode + "/" + role.getRoleName());
        return getRolePermissions(roleCode);
    }

    @Override @Transactional public void deleteRole(String roleCode, AdminActionRequest request, String ip) {
        if (isBuiltInRole(roleCode)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "超级管理员角色不能删除");
        }
        SysRole role = requireRole(roleCode);
        long enabledUsers = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRoleCode, roleCode)
                .eq(SysUser::getStatus, 1));
        if (enabledUsers > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该角色仍绑定使用中的账号，请先停用账号");
        }
        permissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleCode, roleCode));
        roleMapper.deleteById(role.getId());
        audit("DELETE_ROLE", role.getId(), request.getReason(), ip, roleCode + "/" + role.getRoleName());
    }

    @Override public RolePermissionVO getRolePermissions(String roleCode) {
        SysRole role = requireRole(roleCode);
        RolePermissionVO vo = new RolePermissionVO();
        vo.setRoleCode(role.getRoleCode()); vo.setRoleName(role.getRoleName()); vo.setDataScope(role.getDataScope()); vo.setVersion(role.getVersion());
        boolean allPlants = ALL_PLANTS_KEY.equals(role.getDataScope());
        vo.setDataScopeName(allPlants ? "全部分公司数据" : "仅本分公司数据");
        vo.setDataScopeDescription(allPlants ? "可在深圳与梅州之间切换并查看对应数据" : "仅能查看和操作本人所属分公司的数据");
        List<String> permissions = permissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleCode, roleCode)).stream().map(p -> p.getModuleCode() + ":" + p.getActionCode()).collect(Collectors.toList());
        vo.setPermissions(permissions); vo.setPermissionDetails(permissions.stream().map(this::toPermissionDisplay).collect(Collectors.toList())); return vo;
    }

    @Override @Transactional public RolePermissionVO updateRolePermissions(String roleCode, RolePermissionRequest request, String ip) {
        if ("R00".equals(roleCode)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "超级管理员权限已锁定，不能修改");
        }
        if (!OWN_PLANT_KEY.equals(request.getDataScope()) && !ALL_PLANTS_KEY.equals(request.getDataScope())) throw new BusinessException(ResultCode.BAD_REQUEST, "数据范围无效");
        SysRole role = requireRole(roleCode);
        if (!request.getVersion().equals(role.getVersion())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色权限已被其他管理员修改，请刷新后重试");
        }
        role.setDataScope(request.getDataScope());
        if (roleMapper.updateById(role) != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色权限已被其他管理员修改，请刷新后重试");
        }
        List<String> permissions = normalizeSystemAdminPermissions(roleCode, request.getPermissions());
        permissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleCode, roleCode));
        for (String permission : permissions) {
            String[] parts = permission.split(":", 2); if (parts.length != 2) throw new BusinessException(ResultCode.BAD_REQUEST, "权限格式必须为 模块:操作");
            SysRolePermission item = new SysRolePermission(); item.setRoleCode(roleCode); item.setModuleCode(parts[0]); item.setActionCode(parts[1]); permissionMapper.insert(item);
        }
        userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleCode, roleCode)).forEach(this::invalidateAndSave);
        audit("UPDATE_ROLE_PERMISSION", role.getId(), request.getReason(), ip, role.getRoleName()); return getRolePermissions(roleCode);
    }

    @Override public PageResult<AuditLog> listAuditLogs(long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        Page<AuditLog> result = auditLogMapper.selectPage(new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<AuditLog>()
                        .in(AuditLog::getTableName, Arrays.asList("sys_admin", "notification_config", "exception_approval_config"))
                        .orderByDesc(AuditLog::getOperationTime));
        result.getRecords().forEach(this::fillHistoricalAuditContent);
        return PageResult.of(result);
    }

    private void fillHistoricalAuditContent(AuditLog log) {
        if (log.getOperationContent() != null && !log.getOperationContent().isBlank()) return;
        if ("notification_config".equals(log.getTableName())) {
            log.setOperationContent("通知配置已更新（历史记录）");
            return;
        }
        log.setOperationContent(readableAuditContent(log.getOperationType(), log.getAfterData()));
    }

    private void invalidateAndSave(SysUser user) { invalidate(user); userMapper.updateById(user); }
    private void validateDataScope(String dataScope) {
        if (!OWN_PLANT_KEY.equals(dataScope) && !ALL_PLANTS_KEY.equals(dataScope)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "数据范围无效");
        }
    }
    private boolean isBuiltInRole(String roleCode) {
        return "R00".equals(roleCode);
    }
    private void replacePermissions(String roleCode, List<String> permissions) {
        permissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleCode, roleCode));
        for (String permission : permissions) {
            String[] parts = permission.split(":", 2);
            if (parts.length != 2) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "权限格式必须为 模块:操作");
            }
            SysRolePermission item = new SysRolePermission();
            item.setRoleCode(roleCode);
            item.setModuleCode(parts[0]);
            item.setActionCode(parts[1]);
            permissionMapper.insert(item);
        }
    }
    private List<String> normalizeSystemAdminPermissions(String roleCode, List<String> requestedPermissions) {
        List<String> permissions = new ArrayList<>(requestedPermissions == null ? Collections.emptyList() : requestedPermissions);
        boolean hasSystemAdminPermission = permissions.stream().anyMatch(permission -> permission.startsWith("systemAdmin:"));
        if (!SYSTEM_ADMIN_ROLES.contains(roleCode) && hasSystemAdminPermission) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "系统管理菜单仅允许授权给 R00 和 R06");
        }
        if (SYSTEM_ADMIN_ROLES.contains(roleCode)) {
            for (String permission : SYSTEM_ADMIN_PERMISSIONS) {
                if (!permissions.contains(permission)) permissions.add(permission);
            }
        }
        return permissions;
    }
    private void invalidate(SysUser user) { user.setAuthVersion((user.getAuthVersion() == null ? 1 : user.getAuthVersion()) + 1); }
    private SysUser requireUser(Long id) { SysUser user = userMapper.selectById(id); if (user == null) { throw new BusinessException(ResultCode.NOT_FOUND, "账号不存在"); } return user; }
    private SysRole requireRole(String roleCode) { SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode)); if (role == null) { throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在"); } return role; }
    private void validateRoleAndPlant(String roleCode, String plantCode) { requireRole(roleCode); if (!"SZ".equals(plantCode) && !"MZ".equals(plantCode)) { throw new BusinessException(ResultCode.BAD_REQUEST, "分公司仅支持 SZ/MZ"); } }
    private String plantName(String code, String requestedName) { if (requestedName != null && !requestedName.trim().isEmpty()) { return requestedName.trim(); } return "SZ".equals(code) ? "深圳" : "梅州"; }
    private void protectLastSuperAdmin(SysUser user) { if (PermissionService.SUPER_ADMIN_ROLE.equals(user.getRoleCode()) && user.getStatus() != null && user.getStatus() == 1 && userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleCode, "R00").eq(SysUser::getStatus, 1)) <= 1) { throw new BusinessException(ResultCode.BAD_REQUEST, "不能停用最后一个超级管理员"); } }
    private void protectSelfDisable(SysUser user) { LoginUser current = LoginUserHolder.get(); if (current != null && current.getUserId().equals(user.getId())) { throw new BusinessException(ResultCode.BAD_REQUEST, "不能停用自身账号"); } }
    private void protectSelfRoleChange(SysUser user, String newRole) { LoginUser current = LoginUserHolder.get(); if (current != null && current.getUserId().equals(user.getId()) && !user.getRoleCode().equals(newRole)) { throw new BusinessException(ResultCode.BAD_REQUEST, "不能修改自身角色"); }
        if (PermissionService.SUPER_ADMIN_ROLE.equals(user.getRoleCode()) && !PermissionService.SUPER_ADMIN_ROLE.equals(newRole)) { protectLastSuperAdmin(user); } }
    private AdminUserVO toUserVO(SysUser user) { AdminUserVO vo = new AdminUserVO(); vo.setId(user.getId()); vo.setAccount(user.getAccount()); vo.setRealName(user.getRealName()); vo.setRoleCode(user.getRoleCode()); vo.setPlantCode(user.getPlantCode()); vo.setPlantName(user.getPlantName()); vo.setStatus(user.getStatus()); vo.setAuthVersion(user.getAuthVersion()); vo.setLastLoginAt(user.getLastLoginAt() == null ? null : user.getLastLoginAt().format(DATE_TIME)); return vo; }
    private void audit(String action, Long targetId, String reason, String ip, String summary) { LoginUser current = LoginUserHolder.get();
        AuditLog log = new AuditLog(); log.setTableName("sys_admin"); log.setRecordId(targetId); log.setOperationType(action); log.setAfterData(summary); log.setOperationContent(readableAuditContent(action, summary)); log.setOperatorId(current == null ? null : current.getUserId()); log.setOperatorName(current == null ? "SYSTEM" : current.getAccount()); log.setPlantCode(current == null || current.getPlantCode() == null ? "*" : current.getPlantCode().name()); log.setIpAddress(ip); log.setReason(reason); auditLogMapper.insert(log); }
    private String readableAuditContent(String action, String summary) { switch (action) { case "CREATE_USER": return "已创建账号“" + summary + "”"; case "UPDATE_USER": return "账号资料已更新"; case "ENABLE_USER": return "已启用账号“" + summary + "”"; case "DISABLE_USER": return "已停用账号“" + summary + "”"; case "UNLOCK_USER": return "已解除账号“" + summary + "”的锁定"; case "RESET_PASSWORD": return "已重置账号“" + summary + "”的密码"; case "CREATE_ROLE": return "已创建角色“" + summary.substring(summary.indexOf('/') + 1) + "”"; case "DELETE_ROLE": return "已删除角色“" + summary.substring(summary.indexOf('/') + 1) + "”"; case "UPDATE_ROLE_PERMISSION": return "角色“" + summary + "”的权限已调整"; default: return "已完成系统管理操作"; } }
    private PermissionDisplayVO toPermissionDisplay(String code) { String[] parts = code.split(":", 2);
        String module = parts[0];
        String action = parts.length > 1 ? parts[1] : "";
        PermissionDisplayVO vo = new PermissionDisplayVO(); vo.setCode(code); vo.setName(moduleName(module) + " · " + actionName(action)); vo.setDescription(moduleName(module) + actionDescription(action)); return vo; }
    private String moduleName(String code) { switch (code) { case "systemAdmin": return "系统管理"; case "trace": return "来料追溯"; case "incoming": return "来料管理"; case "exception": return "异常管理"; case "fai": return "首件检验"; case "spc": return "SPC 分析"; case "productionDefect": return "生产不良"; case "finishedGoods": return "成品管理"; case "supplier": return "供应商管理"; case "material": return "物料变更"; case "notification": return "系统通知"; default: return code; } }
    private String actionName(String code) { switch (code) { case "VIEW": return "查看"; case "EDIT": return "新增与编辑"; case "APPROVE": return "审核与关闭"; case "EXPORT": return "导出"; default: return code; } }
    private String actionDescription(String code) { switch (code) { case "VIEW": return "：可查看该模块的数据和记录"; case "EDIT": return "：可新增、修改和提交该模块的数据"; case "APPROVE": return "：可审核、确认或关闭该模块的业务流程"; case "EXPORT": return "：可导出该模块的数据"; default: return "：系统权限"; } }
}
