package com.kangli.qms.service.admin.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class RolePermissionRequest {
    @NotBlank private String dataScope;
    private List<String> permissions;
    @NotBlank private String reason;
    @NotNull private Integer version;
}
