package com.kangli.qms.domain.production.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用分组统计项（按等级/来源等维度）。
 */
@Data
@ApiModel(description = "分组统计项")
public class BreakdownItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "分组名称（等级/来源类型）")
    private String name;

    @ApiModelProperty(value = "数量")
    private Integer count;
}
