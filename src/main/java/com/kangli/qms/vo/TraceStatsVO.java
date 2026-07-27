package com.kangli.qms.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 追溯统计 VO（对齐前端 TraceStats 类型）。
 */
@Data
@ApiModel(description = "追溯统计")
public class TraceStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "总节点数")
    private Integer totalNodes;

    @ApiModelProperty(value = "最大层级")
    private Integer maxDepth;

    @ApiModelProperty(value = "层级上限（8）")
    private Integer levelCap;

    @ApiModelProperty(value = "涉及批次数")
    private Integer batchCount;

    @ApiModelProperty(value = "涉及供应商数")
    private Integer supplierCount;
}
