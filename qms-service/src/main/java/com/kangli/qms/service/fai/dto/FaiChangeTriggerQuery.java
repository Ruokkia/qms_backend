package com.kangli.qms.service.fai.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 变更触发分页查询条件（M3）。
 */
@Data
@ApiModel(description = "变更触发分页查询条件")
public class FaiChangeTriggerQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "页码", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页条数", example = "20")
    private Integer size = 20;

    @ApiModelProperty(value = "变更类型：换模具/升级系统/换批次/换设备/材料批次")
    private String triggerType;

    @ApiModelProperty(value = "物料代码")
    private String materialCode;

    @ApiModelProperty(value = "条目类型：PRODUCT(产品) / MATERIAL(物料)")
    private String itemType;

    @ApiModelProperty(value = "批次号")
    private String batchNo;

    @ApiModelProperty(value = "状态：待检验/已检验/关闭")
    private String status;
}
