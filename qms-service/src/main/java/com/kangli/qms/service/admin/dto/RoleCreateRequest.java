package com.kangli.qms.service.admin.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class RoleCreateRequest {
    @NotBlank private String roleName;
    private String description;
    @NotBlank private String dataScope;
    private List<String> permissions;
    @NotBlank private String reason;
}
