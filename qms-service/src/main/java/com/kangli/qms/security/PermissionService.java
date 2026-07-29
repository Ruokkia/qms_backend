package com.kangli.qms.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.domain.admin.entity.SysRolePermission;
import com.kangli.qms.domain.admin.mapper.SysRolePermissionMapper;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
    public static final String SUPER_ADMIN_ROLE = "R00";

    private final SysRolePermissionMapper rolePermissionMapper;

    public PermissionService(SysRolePermissionMapper rolePermissionMapper) {
        this.rolePermissionMapper = rolePermissionMapper;
    }

    public boolean hasPermission(String roleCode, PermissionRequirement requirement) {
        if (SUPER_ADMIN_ROLE.equals(roleCode)) {
            return true;
        }
        return rolePermissionMapper.selectCount(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleCode, roleCode)
                .eq(SysRolePermission::getModuleCode, requirement.getModuleCode())
                .eq(SysRolePermission::getActionCode, requirement.getAction().name())) > 0;
    }
}
