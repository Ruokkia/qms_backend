package com.kangli.qms.domain.supplier.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 高风险物料清单 VO。
 */
@Data
@ApiModel(description = "高风险物料清单项")
public class HighRiskMaterialVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "物料ID")
    private Long id;

    @ApiModelProperty(value = "物料编码")
    private String materialCode;

    @ApiModelProperty(value = "物料名称")
    private String materialName;

    @ApiModelProperty(value = "风险等级")
    private String riskLevel;

    @ApiModelProperty(value = "额外资料要求")
    private String extraRequirement;

    @ApiModelProperty(value = "备注")
    private String remark;
}
