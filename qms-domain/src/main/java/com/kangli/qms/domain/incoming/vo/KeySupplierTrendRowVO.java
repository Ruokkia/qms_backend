package com.kangli.qms.domain.incoming.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 重点供应商趋势矩阵行（日期 × 供应商）。
 */
@Data
@ApiModel(description = "重点供应商趋势矩阵行")
public class KeySupplierTrendRowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "供应商编号")
    private String supplierCode;

    @ApiModelProperty(value = "供应商名称")
    private String supplierName;

    @ApiModelProperty(value = "日期（YYYY-MM-DD）")
    private String date;

    @ApiModelProperty(value = "当日合格率（%），无来料为 null")
    private BigDecimal passRate;
}
