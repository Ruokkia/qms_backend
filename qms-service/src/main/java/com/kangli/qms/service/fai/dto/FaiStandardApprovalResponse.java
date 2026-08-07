package com.kangli.qms.service.fai.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * FAI 检验标准审批响应 DTO（P1：轻量级审批工作流）。
 */
@Data
@ApiModel(description = "FAI 检验标准审批记录")
public class FaiStandardApprovalResponse {

    @ApiModelProperty("审批记录 ID")
    private Long id;

    @ApiModelProperty("关联标准 ID")
    private Long standardId;

    @ApiModelProperty("审批类型：CREATE / UPDATE / DELETE")
    private String approvalType;

    @ApiModelProperty("待审批的请求数据 JSON 字符串")
    private String requestData;

    @ApiModelProperty("提交人")
    private String requester;

    @ApiModelProperty("提交时间")
    private LocalDateTime requestedAt;

    @ApiModelProperty("审批人")
    private String approver;

    @ApiModelProperty("审批时间")
    private LocalDateTime approvedAt;

    @ApiModelProperty("审批状态：PENDING / APPROVED / REJECTED")
    private String approvalStatus;

    @ApiModelProperty("驳回原因")
    private String rejectReason;

    @ApiModelProperty("是否已执行")
    private Boolean applied;

    @ApiModelProperty("提交备注")
    private String remark;

    @ApiModelProperty("厂区编码")
    private String plantCode;

    @ApiModelProperty("厂区名称")
    private String plantName;
}
