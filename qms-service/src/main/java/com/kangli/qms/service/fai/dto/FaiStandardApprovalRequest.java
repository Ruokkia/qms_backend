package com.kangli.qms.service.fai.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * FAI 检验标准审批请求
 */
@Data
@ApiModel(description = "FAI 检验标准审批请求")
public class FaiStandardApprovalRequest {

    @ApiModelProperty("标准 ID（编辑/删除时必填，新建时不填）")
    private Long standardId;

    @ApiModelProperty("审批类型：CREATE / UPDATE / DELETE")
    private String approvalType;

    @ApiModelProperty("待审批的请求数据（FaiStandardSaveRequest 的 JSON 字符串）")
    private String requestData;

    @ApiModelProperty("提交备注")
    private String remark;
}
