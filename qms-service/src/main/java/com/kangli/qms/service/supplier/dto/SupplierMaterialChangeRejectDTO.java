package com.kangli.qms.service.supplier.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 供应商物料变更审批驳回请求。
 */
@Data
@ApiModel(description = "供应商物料变更审批驳回请求")
public class SupplierMaterialChangeRejectDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "审批角色不能为空")
    @ApiModelProperty(value = "审批角色：QUALITY 质量 / PURCHASE 采购 / RD 研发", required = true)
    private String approvalRole;

    @NotBlank(message = "驳回原因不能为空")
    @ApiModelProperty(value = "驳回原因", required = true)
    private String rejectReason;
}
