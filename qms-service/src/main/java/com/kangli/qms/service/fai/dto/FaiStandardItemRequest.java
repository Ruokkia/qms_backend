package com.kangli.qms.service.fai.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 首件检验标准模板 参数项请求 DTO —— 用于标准新增/更新的参数项明细。
 * <p>字段与 qms.fai_inspection_standard_item 实体对应，新增时 id 留空，更新时由前端回传。</p>
 */
@Data
@ApiModel(value = "FaiStandardItemRequest", description = "首件检验标准参数项")
public class FaiStandardItemRequest {

    @ApiModelProperty(value = "参数项 id（更新时由前端回传，新增时留空）")
    private Long id;

    @ApiModelProperty(value = "参数名称", required = true)
    private String paramName;

    @ApiModelProperty(value = "参数代码")
    private String paramCode;

    @ApiModelProperty(value = "参数类别：AQL/关键尺寸/性能参数", required = true)
    private String paramCategory;

    @ApiModelProperty(value = "标准值")
    private String standardValue;

    @ApiModelProperty(value = "上限")
    private BigDecimal upperLimit;

    @ApiModelProperty(value = "下限")
    private BigDecimal lowerLimit;

    @ApiModelProperty(value = "单位")
    private String unit;

    @ApiModelProperty(value = "是否必检：是/否")
    private String isRequired;

    @ApiModelProperty(value = "排序号")
    private Integer sortOrder;

    @ApiModelProperty(value = "是否自动纳入SPC：是/否")
    private String spcEnabled;

    @ApiModelProperty(value = "SPC参数ID；纳入SPC时必填，服务端据此写入数值标准")
    private Long spcParameterId;
}
