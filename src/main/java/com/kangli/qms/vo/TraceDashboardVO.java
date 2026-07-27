package com.kangli.qms.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 追溯看板统计 VO（对齐前端 TraceDashboard 类型，KPI）。
 */
@Data
@ApiModel(description = "追溯看板统计")
public class TraceDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "来料批次总数")
    private Integer totalBatches;

    @ApiModelProperty(value = "合格批次数")
    private Integer qualifiedBatches;

    @ApiModelProperty(value = "异常批次数")
    private Integer abnormalBatches;

    @ApiModelProperty(value = "在检批次数")
    private Integer inCheckBatches;

    @ApiModelProperty(value = "一次交检合格率（%）")
    private BigDecimal passRate;

    @ApiModelProperty(value = "不良率 PPM")
    private Integer ppm;

    @ApiModelProperty(value = "供应商数")
    private Integer supplierCount;

    @ApiModelProperty(value = "整机SN数")
    private Integer snCount;

    @ApiModelProperty(value = "追溯节点总数")
    private Integer nodeCount;
}
