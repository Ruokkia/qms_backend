package com.kangli.qms.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 刷新 Token 请求 DTO。
 */
@Data
@ApiModel(description = "刷新Token请求参数")
public class RefreshDTO {

    @NotBlank(message = "refreshToken不能为空")
    @ApiModelProperty(value = "Refresh Token", required = true)
    private String refreshToken;
}
