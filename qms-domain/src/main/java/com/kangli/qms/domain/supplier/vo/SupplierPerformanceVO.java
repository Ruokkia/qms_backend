package com.kangli.qms.domain.supplier.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 供应商绩效评估 VO（多维指标 + 综合评分 + 绩效等级）。
 */
@Data
@ApiModel(description = "供应商绩效评估")
public class SupplierPerformanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "供应商ID")
    private Long supplierId;

    @ApiModelProperty(value = "供应商名称")
    private String supplierName;

    @ApiModelProperty(value = "供应商编码")
    private String supplierCode;

    @ApiModelProperty(value = "风险等级")
    private String riskLevel;

    @ApiModelProperty(value = "来料批次数")
    private Integer totalBatches;

    @ApiModelProperty(value = "来料合格率(%)")
    private BigDecimal passRate;

    @ApiModelProperty(value = "来料不良率(%)")
    private BigDecimal unqualifiedRate;

    @ApiModelProperty(value = "整改及时率(%)")
    private BigDecimal rectifyOnTimeRate;

    @ApiModelProperty(value = "资质合规率(%)")
    private BigDecimal complianceRate;

    @ApiModelProperty(value = "交付及时率(%)，数据未接入时为 null")
    private BigDecimal deliveryOnTimeRate;

    @ApiModelProperty(value = "综合评分(0-100)")
    private BigDecimal score;

    @ApiModelProperty(value = "绩效等级：A/B/C/D")
    private String grade;
}
