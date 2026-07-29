package com.kangli.qms.service.admin;

import com.kangli.qms.service.admin.dto.AdminActionRequest;
import com.kangli.qms.service.admin.dto.AdminUserRequest;
import com.kangli.qms.service.admin.dto.RolePermissionRequest;
import com.kangli.qms.service.admin.dto.RoleCreateRequest;
import com.kangli.qms.domain.admin.vo.AdminUserVO;
import com.kangli.qms.domain.admin.vo.RolePermissionVO;
import java.util.List;
import com.kangli.qms.domain.admin.entity.AuditLog;

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
    RolePermissionVO createRole(RoleCreateRequest request, String ip);
    void deleteRole(String roleCode, AdminActionRequest request, String ip);
    List<AuditLog> listAuditLogs();
}
