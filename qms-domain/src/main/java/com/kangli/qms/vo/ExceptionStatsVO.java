package com.kangli.qms.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 异常整改 KPI 看板统计 VO（对齐接口文档 m2-6 stats）。
 */
@Data
@ApiModel(description = "异常整改KPI看板")
public class ExceptionStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "异常总数")
    private Integer totalExceptions;

    @ApiModelProperty(value = "待整改数")
    private Integer pendingCount;

    @ApiModelProperty(value = "整改中数")
    private Integer inProgressCount;

    @ApiModelProperty(value = "待验证数")
    private Integer pendingVerifyCount;

    @ApiModelProperty(value = "已闭环数")
    private Integer closedCount;

    @ApiModelProperty(value = "闭环率（%）")
    private BigDecimal closureRate;

    @ApiModelProperty(value = "按严重等级分布")
    private List<BreakdownItemVO> severityBreakdown;

    @ApiModelProperty(value = "按来源分布")
    private List<BreakdownItemVO> sourceBreakdown;

    @ApiModelProperty(value = "超期未闭环数")
    private Integer overdueCount;

    @ApiModelProperty(value = "当前活跃升级数")
    private Integer escalationCount;
}
