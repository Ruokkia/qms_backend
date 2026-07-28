package com.kangli.qms.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 批量升级检查请求 DTO（对齐接口文档 m2-8）。
 */
@Data
@ApiModel(description = "批量升级检查请求")
public class EscalationCheckDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "供应商ID（不传则检查全部）")
    private Long supplierId;

    @ApiModelProperty(value = "重复问题窗口天数（默认90）")
    private Integer daysWindow;

    @ApiModelProperty(value = "触发阈值（默认3次）")
    private Integer minRepeatCount;
}
