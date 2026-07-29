package com.kangli.qms.domain.auth.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录响应 VO（双 Token + 用户信息）。
 * <p>与前端 LoginResponse 类型对齐。</p>
 */
@Data
@ApiModel(description = "登录响应（含双Token）")
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "JWT Access Token，有效期2小时", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @ApiModelProperty(value = "Refresh Token，有效期7天", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String refreshToken;

    @ApiModelProperty(value = "Access Token过期时间（秒）", example = "7200")
    private long tokenExpireIn;

    @ApiModelProperty(value = "用户信息")
    private UserInfoVO userInfo;
}
