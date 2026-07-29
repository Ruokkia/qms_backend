package com.kangli.qms.domain.production.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 日统计趋势项。
 */
@Data
@ApiModel(description = "日统计趋势")
public class DailyTrendItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "日期")
    private String date;

    @ApiModelProperty(value = "总批次数")
    private Integer totalBatches;

    @ApiModelProperty(value = "合格率（%）")
    private BigDecimal passRate;
}
