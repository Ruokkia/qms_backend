package com.kangli.qms.service.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AdminUserRequest {
    @NotBlank private String account;
    @NotBlank private String realName;
    @NotBlank private String roleCode;
    @NotBlank private String plantCode;
    private String plantName;
    private String password;
    private String reason;
}
