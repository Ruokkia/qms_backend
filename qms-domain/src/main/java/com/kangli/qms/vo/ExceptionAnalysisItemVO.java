package com.kangli.qms.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 多维度分析项（单条聚合结果）。
 */
@Data
@ApiModel(description = "多维度分析项")
public class ExceptionAnalysisItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "维度名称（不良描述/供应商名/物料代码/日期）")
    private String name;

    @ApiModelProperty(value = "数量")
    private Integer count;

    @ApiModelProperty(value = "占比（%）")
    private BigDecimal ratio;
}
