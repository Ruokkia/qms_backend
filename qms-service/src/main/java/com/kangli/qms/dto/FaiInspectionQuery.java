package com.kangli.qms.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 首件检验记录分页查询条件（M3）。
 */
@Data
@ApiModel(description = "首件检验记录分页查询条件")
public class FaiInspectionQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "页码", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页条数", example = "20")
    private Integer size = 20;

    @ApiModelProperty(value = "首件编号（精确/模糊）")
    private String faiNo;

    @ApiModelProperty(value = "批次号（精确）")
    private String batchNo;

    @ApiModelProperty(value = "物料名称（模糊）")
    private String materialName;

    @ApiModelProperty(value = "判定结果：待判定/合格/不合格")
    private String inspectionResult;
}
