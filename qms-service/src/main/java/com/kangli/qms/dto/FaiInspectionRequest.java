package com.kangli.qms.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 从变更触发创建首件检验单请求（M3）。
 */
@Data
@ApiModel(description = "创建首件检验单请求")
public class FaiInspectionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "变更触发ID不能为空")
    @ApiModelProperty(value = "变更触发ID（关联 fai_change_trigger.id）", required = true)
    private Long changeTriggerId;
}
