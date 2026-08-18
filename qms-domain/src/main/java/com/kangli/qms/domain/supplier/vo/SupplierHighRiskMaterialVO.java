package com.kangli.qms.domain.supplier.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商高风险物料关联 VO（含额外资料要求）。
 */
@Data
@ApiModel(description = "供应商高风险物料及额外审核要求")
public class SupplierHighRiskMaterialVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "关联ID")
    private Long id;

    @ApiModelProperty(value = "供应商ID")
    private Long supplierId;

    @ApiModelProperty(value = "供应商名称")
    private String supplierName;

    @ApiModelProperty(value = "物料ID")
    private Long materialId;

    @ApiModelProperty(value = "物料编码")
    private String materialCode;

    @ApiModelProperty(value = "物料名称")
    private String materialName;

    @ApiModelProperty(value = "物料风险等级")
    private String riskLevel;

    @ApiModelProperty(value = "额外资料要求")
    private String extraRequirement;
}
