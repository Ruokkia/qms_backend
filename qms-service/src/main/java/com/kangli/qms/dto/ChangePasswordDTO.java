package com.kangli.qms.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class ChangePasswordDTO {
    @NotBlank(message = "当前密码不能为空")
    private String currentPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码至少 6 位")
    private String newPassword;

    @NotBlank(message = "确认新密码不能为空")
    private String confirmPassword;
}
