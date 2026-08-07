package com.kangli.qms.service.exception.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 8D/CAPA 阶段审批请求 DTO（通过 / 驳回）。
 */
@Data
@ApiModel(description = "阶段审批请求（通过 / 驳回）")
public class StageApprovalDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "流程维度不能为空")
    @ApiModelProperty(value = "流程维度：8D / CAPA", required = true)
    private String processFlow;

    @NotBlank(message = "阶段不能为空")
    @ApiModelProperty(value = "阶段码：8D 为 D0-D8，CAPA 为 C1-C4", required = true)
    private String stage;

    @ApiModelProperty(value = "审批意见（通过理由 / 驳回理由）")
    private String comment;
}
