package com.kangli.qms.domain.admin.vo;

import lombok.Data;
import java.util.List;

@Data
public class RolePermissionVO {
    private String roleCode;
    private String roleName;
    private String dataScope;
    private String dataScopeName;
    private String dataScopeDescription;
    private Integer version;
    private List<String> permissions;
    private List<PermissionDisplayVO> permissionDetails;
}
