package com.kangli.qms.service.exception.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * CAPA 治理阶段审批请求 DTO。
 * BOTH 模式下，CAPA 作为治理层在 8D 推进到关键节点时介入审批。
 */
@Data
@ApiModel("CAPA 相位审批请求")
public class CapaPhaseApprovalDTO {

    @ApiModelProperty(value = "审批意见", required = true, example = "根因分析合理，批准进入措施阶段")
    @NotBlank(message = "审批意见不能为空")
    private String comment;
}
