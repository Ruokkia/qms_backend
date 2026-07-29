package com.kangli.qms.service.exception.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 异常闭环请求 DTO（对齐接口文档 m2-7）。
 */
@Data
@ApiModel(description = "异常闭环请求")
public class ExceptionCloseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "闭环原因不能为空")
    @ApiModelProperty(value = "闭环原因/总结", required = true)
    private String closeReason;
}
