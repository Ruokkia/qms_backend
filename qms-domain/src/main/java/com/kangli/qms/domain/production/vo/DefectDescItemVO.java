package com.kangli.qms.domain.production.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 高频不合格描述项。
 */
@Data
@ApiModel(description = "高频不合格描述")
public class DefectDescItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "不合格描述")
    private String defectDesc;

    @ApiModelProperty(value = "出现次数")
    private Integer count;
}
