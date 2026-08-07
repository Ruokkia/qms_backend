package com.kangli.qms.service.exception.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 阶段级审批配置保存/更新 DTO（系统管理模块 CRUD）。
 */
@Data
@ApiModel(description = "阶段级审批配置保存/更新请求")
public class ExceptionApprovalConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键（更新时必传，新增不传）")
    private Long id;

    @NotBlank(message = "流程维度不能为空")
    @ApiModelProperty(value = "流程维度：8D / CAPA", required = true)
    private String processFlow;

    @NotBlank(message = "阶段不能为空")
    @ApiModelProperty(value = "阶段码：8D 为 D0-D8，CAPA 为 C1-C4", required = true)
    private String stage;

    @ApiModelProperty(value = "阶段中文名（展示用）")
    private String stageName;

    @NotNull(message = "是否需审批不能为空")
    @ApiModelProperty(value = "是否需审批：0=否 1=是", required = true)
    private Short needApproval;

    @ApiModelProperty(value = "审批角色：R04=质量工程师 R06=质量经理（需审批时必传）")
    private String approverRole;

    @ApiModelProperty(value = "是否默认配置（全局默认一套）")
    private Short isDefault;
}
