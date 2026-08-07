package com.kangli.qms.service.exception.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;

/**
 * 异常单发起整改流程 DTO（质量部选流程 + D0 发起 + 指派人员）。
 */
@Data
@ApiModel(description = "异常单发起整改流程请求（选流程 + 指派）")
public class ExceptionInitiateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "整改流程类型不能为空")
    @ApiModelProperty(value = "整改流程类型：CAPA / 8D / BOTH（质量部门手动选择，系统不预填）", required = true)
    private String processType;

    @ApiModelProperty(value = "整改责任人 ID（自动触发时留空，相关部门从已有人员中选择填写）")
    private Long ownerId;

    @ApiModelProperty(value = "整改责任人姓名（与发起操作人 initiatedBy 区分）")
    private String ownerName;

    @ApiModelProperty(value = "D0 质量部发起说明（立案情由 / 不良现象概述）")
    private String d0Symptom;

    @ApiModelProperty(value = "D0 发起责任人（质量部发起者姓名），不传则取当前登录人")
    private String d0Initiator;

    @ApiModelProperty(value = "8D 团队姓名逗号串（发起时指派的 8D 团队，预填进 D1）")
    private String d1Team;

    @ApiModelProperty(value = "CAPA 负责人姓名逗号串（发起时指派的 CAPA 负责人）")
    private String capaOwner;
}
