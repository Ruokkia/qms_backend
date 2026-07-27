package com.kangli.qms.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * IQC 检验明细项 VO（对齐前端 TraceInspectionItem 类型）。
 */
@Data
@ApiModel(description = "IQC检验明细项")
public class TraceInspectionItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "ID")
    private Long id;

    @ApiModelProperty(value = "检验项")
    private String item;

    @ApiModelProperty(value = "规格要求")
    private String spec;

    @ApiModelProperty(value = "实测值")
    private String measured;

    @ApiModelProperty(value = "判定：合格/不合格")
    private String judgment;

    @ApiModelProperty(value = "检验员")
    private String inspector;

    @ApiModelProperty(value = "检验时间")
    private String time;
}
