package com.kangli.qms.dto;

import com.kangli.qms.entity.FaiChangeTrigger;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 变更触发响应（M3）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "变更触发响应")
public class FaiChangeTriggerResponse extends FaiChangeTrigger implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "是否已生成首件检验单")
    private Boolean hasInspection;
}
