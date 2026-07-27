package com.kangli.qms.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 追溯节点 VO（对齐前端 TraceNode 类型）。
 */
@Data
@ApiModel(description = "追溯节点")
public class TraceNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "节点ID")
    private Long id;

    @ApiModelProperty(value = "节点类型：SN/部件/关键物料/非关键物料/来料批次/生产批次")
    private String nodeType;

    @ApiModelProperty(value = "节点编码")
    private String nodeCode;

    @ApiModelProperty(value = "直接父级ID（NULL=根节点）")
    private Long parentId;

    @ApiModelProperty(value = "父级编码（连表填充）")
    private String parentCode;

    @ApiModelProperty(value = "关联批次ID")
    private Long batchId;

    @ApiModelProperty(value = "该节点用量")
    private BigDecimal qtyUsed;

    @ApiModelProperty(value = "关联工单ID")
    private Long workOrderId;

    @ApiModelProperty(value = "分公司编码")
    private String plantCode;

    @ApiModelProperty(value = "层级（根=1）")
    private Integer level;

    @ApiModelProperty(value = "完整路径")
    private String path;

    @ApiModelProperty(value = "关联批次信息")
    private TraceBatchInfoVO batchInfo;

    @ApiModelProperty(value = "子节点（树结构）")
    private List<TraceNodeVO> children;
}
