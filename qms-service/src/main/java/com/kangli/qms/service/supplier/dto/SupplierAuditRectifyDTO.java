package com.kangli.qms.service.supplier.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 提交整改措施 / 验证结果请求。
 */
@Data
@ApiModel(description = "整改跟踪请求（提交措施或验证结果）")
public class SupplierAuditRectifyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "整改措施")
    private String measure;

    @ApiModelProperty(value = "整改截止日期 yyyy-MM-dd")
    private String dueDate;

    @ApiModelProperty(value = "整改责任人")
    private String owner;

    @ApiModelProperty(value = "验证结果：通过/不通过")
    private String verifyResult;

    @ApiModelProperty(value = "验证人")
    private String verifiedBy;
}
