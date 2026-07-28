package com.kangli.qms.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 登录请求 DTO。
 */
@Data
@ApiModel(description = "登录请求参数")
public class LoginDTO {

    @NotBlank(message = "账号不能为空")
    @ApiModelProperty(value = "登录账号", example = "sz_op01", required = true)
    private String account;

    @NotBlank(message = "密码不能为空")
    @ApiModelProperty(value = "密码", example = "123456", required = true)
    private String password;

    @NotBlank(message = "分公司编码不能为空")
    @ApiModelProperty(value = "分公司编码 SZ=深圳 MZ=梅州", example = "SZ", required = true)
    private String plantCode;

}
