package com.kangli.qms.domain.auth.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 验证码响应 VO。
 * <p>返回验证码 key（登录时回传）+ base64 图片（前端直接渲染）。</p>
 */
@Data
@ApiModel(description = "图形验证码")
public class CaptchaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "验证码 key（登录时随 captchaKey 回传）", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String captchaKey;

    @ApiModelProperty(value = "base64 图片（data:image/png;base64,...）", example = "data:image/png;base64,iVBORw0KGgo...")
    private String captchaImage;

    @ApiModelProperty(value = "过期时间（秒）", example = "300")
    private long expireIn;
}
