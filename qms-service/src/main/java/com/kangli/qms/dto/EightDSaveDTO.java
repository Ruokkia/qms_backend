package com.kangli.qms.dto;

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
    @ApiModelProperty(value = "当前步骤：D1-D8", required = true)
    private String currentStep;

    @ApiModelProperty(value = "D1 团队成立")
    private String d1Team;

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
