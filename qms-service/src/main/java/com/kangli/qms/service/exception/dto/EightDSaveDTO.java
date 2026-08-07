package com.kangli.qms.service.exception.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 8D 报告保存/更新 DTO。
 */
@Data
@ApiModel(description = "8D 报告保存/更新请求")
public class EightDSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "当前步骤不能为空")
    @ApiModelProperty(value = "当前步骤：8D 为 D0-D8，CAPA 流程另见 capaCurrentStep", required = true)
    private String currentStep;

    @ApiModelProperty(value = "D0 质量部发起说明（立案情由 / 不良现象概述）")
    private String d0Symptom;

    @ApiModelProperty(value = "D0 发起责任人（质量部发起者姓名）")
    private String d0Initiator;

    @ApiModelProperty(value = "D0 发起时间")
    private java.time.LocalDateTime d0InitiateTime;

    @ApiModelProperty(value = "D1 团队成立（JSON 数组：成员姓名列表）")
    private String d1Team;

    @ApiModelProperty(value = "CAPA 负责人姓名列表（JSON 数组，与 8D 团队对称指派）")
    private String capaOwner;

    @ApiModelProperty(value = "CAPA 流程当前阶段：C1-C4（选 CAPA 或 BOTH 时维护）")
    private String capaCurrentStep;

    @ApiModelProperty(value = "D2 问题描述（5W2H）")
    private String d2ProblemDesc;

    @ApiModelProperty(value = "D3 临时遏制措施")
    private String d3Containment;

    @ApiModelProperty(value = "D4 根本原因分析")
    private String d4RootCause;

    @ApiModelProperty(value = "D5 纠正措施")
    private String d5Corrective;

    @ApiModelProperty(value = "D6 实施与验证")
    private String d6Implementation;

    @ApiModelProperty(value = "D7 预防措施")
    private String d7Preventive;

    @ApiModelProperty(value = "D8 团队表彰/闭环总结")
    private String d8Closure;

    @ApiModelProperty(value = "数据版本号（并发控制，不传则降级为乐观锁保护）")
    private Integer version;
}
