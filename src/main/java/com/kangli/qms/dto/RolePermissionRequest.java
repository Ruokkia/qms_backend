package com.kangli.qms.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class RolePermissionRequest {
    @NotBlank private String dataScope;
    private List<String> permissions;
    @NotBlank private String reason;
}
