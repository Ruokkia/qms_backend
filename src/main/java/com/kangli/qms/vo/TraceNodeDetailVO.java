package com.kangli.qms.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 追溯节点详情 VO（对齐前端 TraceNodeDetail 类型）。
 */
@Data
@ApiModel(description = "追溯节点详情")
public class TraceNodeDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "节点详情")
    private TraceNodeVO detail;

    @ApiModelProperty(value = "关联批次信息")
    private TraceBatchInfoVO batchInfo;

    @ApiModelProperty(value = "父节点（若有）")
    private TraceNodeVO parent;

    @ApiModelProperty(value = "直接子节点列表")
    private List<TraceNodeVO> children;

    @ApiModelProperty(value = "IQC 检验明细")
    private List<TraceInspectionItemVO> inspections;
}
