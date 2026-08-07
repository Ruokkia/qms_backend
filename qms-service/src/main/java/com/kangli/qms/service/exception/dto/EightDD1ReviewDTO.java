package com.kangli.qms.service.exception.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * D1 团队审核请求（质量部门审核负责人提交的团队构成）。
 */
@Data
@ApiModel(description = "D1 团队审核请求")
public class EightDD1ReviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "审核结果不能为空")
    @ApiModelProperty(value = "是否通过：true=通过，false=驳回", required = true)
    private Boolean approved;

    @ApiModelProperty(value = "审核意见（驳回时必填）")
    private String reviewComment;

    @ApiModelProperty(value = "版本号（乐观锁）", required = true)
    @NotNull(message = "版本号不能为空")
    private Integer version;
}
