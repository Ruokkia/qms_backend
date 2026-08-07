package com.kangli.qms.domain.exception.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阶段级审批配置 VO。
 */
@Data
@ApiModel(description = "阶段级审批配置")
public class ExceptionApprovalConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键")
    private Long id;

    @ApiModelProperty(value = "流程维度：8D / CAPA")
    private String processFlow;

    @ApiModelProperty(value = "阶段码：8D 为 D0-D8，CAPA 为 C1-C4")
    private String stage;

    @ApiModelProperty(value = "阶段中文名")
    private String stageName;

    @ApiModelProperty(value = "是否需审批：0=否 1=是")
    private Short needApproval;

    @ApiModelProperty(value = "审批角色：R04=质量工程师 R06=质量经理")
    private String approverRole;

    @ApiModelProperty(value = "是否默认配置")
    private Short isDefault;

    @ApiModelProperty(value = "分公司编码")
    private String plantCode;

    @ApiModelProperty(value = "分公司名称")
    private String plantName;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updatedAt;
}
