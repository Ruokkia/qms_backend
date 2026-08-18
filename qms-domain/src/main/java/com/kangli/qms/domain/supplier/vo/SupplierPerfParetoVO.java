package com.kangli.qms.domain.supplier.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商绩效柏拉图项（按质量问题描述分类的来料不合格数量分布）。
 */
@Data
@ApiModel(description = "绩效柏拉图项")
public class SupplierPerfParetoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "缺陷分类（按 defect_desc 归一）")
    private String category;

    @ApiModelProperty(value = "不合格批次数")
    private Long count;

    @ApiModelProperty(value = "累计占比(%)")
    private java.math.BigDecimal cumulativeRate;
}
