package com.kangli.qms.service.fai.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * FAI 检验标准变更历史响应 DTO（P0：变更追溯）。
 */
@Data
@ApiModel(description = "FAI 检验标准变更历史")
public class FaiStandardHistoryResponse {

    @ApiModelProperty("历史记录 ID")
    private Long id;

    @ApiModelProperty("关联标准 ID")
    private Long standardId;

    @ApiModelProperty("变更类型：CREATE / UPDATE / DELETE")
    private String changeType;

    @ApiModelProperty("变更原因")
    private String changeReason;

    @ApiModelProperty("变更前快照 JSON 字符串")
    private String beforeSnapshot;

    @ApiModelProperty("变更后快照 JSON 字符串")
    private String afterSnapshot;

    @ApiModelProperty("自动生成的差异摘要")
    private String diffSummary;

    @ApiModelProperty("操作人账号")
    private String changedBy;

    @ApiModelProperty("操作时间")
    private LocalDateTime changedAt;

    @ApiModelProperty("厂区编码")
    private String plantCode;

    @ApiModelProperty("厂区名称")
    private String plantName;
}
