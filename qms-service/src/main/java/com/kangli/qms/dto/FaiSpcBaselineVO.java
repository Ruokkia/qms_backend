package com.kangli.qms.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPC 调取基准数据（M3，按 param_code 分组的实际值）。
 */
@Data
@ApiModel(description = "SPC 调取基准数据")
public class FaiSpcBaselineVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "子组号（取首件编号）")
    private String subgroupNo;

    @ApiModelProperty(value = "参数编码")
    private String paramCode;

    @ApiModelProperty(value = "参数名称")
    private String paramName;

    @ApiModelProperty(value = "实际值")
    private BigDecimal value;

    @ApiModelProperty(value = "采样时间（首件创建时间）")
    private LocalDateTime sampleTime;

    @ApiModelProperty(value = "分公司编码")
    private String plantCode;
}
