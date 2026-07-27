package com.kangli.qms.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 追溯查询响应 VO（对齐前端 TraceTreeResult 类型，树形结构）。
 */
@Data
@ApiModel(description = "追溯查询结果（树形）")
public class TraceTreeResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "起始节点（根）")
    private TraceNodeVO rootNode;

    @ApiModelProperty(value = "子节点树（向下追溯时填充）")
    private List<TraceNodeVO> children;

    @ApiModelProperty(value = "向上链（双向/向上追溯时填充，按起点→顶层顺序）")
    private List<TraceNodeVO> upward;

    @ApiModelProperty(value = "追溯统计")
    private TraceStatsVO stats;
}
