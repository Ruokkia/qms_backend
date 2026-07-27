package com.kangli.qms.service;

import com.kangli.qms.dto.AdminActionRequest;
import com.kangli.qms.dto.AdminUserRequest;
import com.kangli.qms.dto.RolePermissionRequest;
import com.kangli.qms.vo.AdminUserVO;
import com.kangli.qms.vo.RolePermissionVO;
import java.util.List;
import com.kangli.qms.entity.AuditLog;

public interface AdminService {
    List<AdminUserVO> listUsers();
    AdminUserVO createUser(AdminUserRequest request, String ip);
    AdminUserVO updateUser(Long id, AdminUserRequest request, String ip);
    void setUserStatus(Long id, boolean enabled, AdminActionRequest request, String ip);
    void unlockUser(Long id, AdminActionRequest request, String ip);
    void resetPassword(Long id, AdminActionRequest request, String ip);
    List<RolePermissionVO> listRoles();
    RolePermissionVO getRolePermissions(String roleCode);
    RolePermissionVO updateRolePermissions(String roleCode, RolePermissionRequest request, String ip);
    List<AuditLog> listAuditLogs();
}
