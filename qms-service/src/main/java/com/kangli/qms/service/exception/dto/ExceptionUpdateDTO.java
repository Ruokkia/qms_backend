package com.kangli.qms.service.exception.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 异常单更新请求 DTO（字段白名单）。
 * <p>仅含业务可编辑字段，plantCode / exceptionNo / createdBy / status / closedAt /
 * sourceId / ruleReason / repeatCount* / problemFingerprint / handlingMethod / version 等
 * 系统字段一律不在白名单内，从根上杜绝前端篡改。</p>
 * <p>加 {@code @JsonIgnoreProperties(ignoreUnknown = true)}，前端继续发送
 * {@code Partial<ExceptionOrder>}（含 plantCode 等字段）时反序列化不报错，前端零改动。</p>
 */
@Data
@ApiModel(description = "异常单更新请求（字段白名单，禁止前端篡改 plantCode/exceptionNo/createdBy 等系统字段）")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExceptionUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("来源类型：来料不良/制程不良/审核问题/客户投诉/重复问题")
    private String sourceType;

    @ApiModelProperty("严重等级：严重/一般")
    private String severity;

    @ApiModelProperty("供应商ID")
    private Long supplierId;

    @ApiModelProperty("关联工单ID")
    private Long workOrderId;

    @ApiModelProperty("物料代码")
    private String materialCode;

    @ApiModelProperty("不良描述")
    private String defectDesc;

    @ApiModelProperty("不良数量")
    private BigDecimal defectQty;

    @ApiModelProperty("总数量")
    private BigDecimal totalQty;

    @ApiModelProperty("处理人ID")
    private Long handlerId;

    @ApiModelProperty("审核人/复核人ID")
    private Long reviewerId;

    @ApiModelProperty("整改截止日期")
    private LocalDate deadline;

    @ApiModelProperty("备注")
    private String remark;

    @ApiModelProperty("电子签名用户")
    private String signatureUser;

    @ApiModelProperty("电子签名时间")
    private LocalDateTime signatureTime;

    @ApiModelProperty("电子签名原因")
    private String signatureReason;
}
