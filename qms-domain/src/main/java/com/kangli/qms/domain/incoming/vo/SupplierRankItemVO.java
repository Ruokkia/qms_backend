package com.kangli.qms.domain.incoming.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 供应商合格率排名项。
 */
@Data
@ApiModel(description = "供应商合格率排名")
public class SupplierRankItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "供应商名称")
    private String supplierName;

    @ApiModelProperty(value = "供应商编号")
    private String supplierCode;

    @ApiModelProperty(value = "总批次数")
    private Integer totalBatches;

    @ApiModelProperty(value = "合格批次数")
    private Integer qualifiedBatches;

    @ApiModelProperty(value = "不合格批次数")
    private Integer unqualifiedBatches;

    @ApiModelProperty(value = "合格率（%）")
    private BigDecimal passRate;

    @ApiModelProperty(value = "不合格率（%）")
    private BigDecimal unqualifiedRate;
}
