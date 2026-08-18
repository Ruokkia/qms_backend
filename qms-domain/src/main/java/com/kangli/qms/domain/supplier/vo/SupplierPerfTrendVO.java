package com.kangli.qms.domain.supplier.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 供应商绩效月度趋势 VO。
 */
@Data
@ApiModel(description = "供应商绩效月度趋势")
public class SupplierPerfTrendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "月份，如 2026-08")
    private String month;

    @ApiModelProperty(value = "平均来料合格率(%)")
    private BigDecimal avgPassRate;

    @ApiModelProperty(value = "平均综合评分")
    private BigDecimal avgScore;

    @ApiModelProperty(value = "来料批次数")
    private Integer batches;
}
