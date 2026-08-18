package com.kangli.qms.service.supplier.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 创建审核计划请求（年度自动生成时仅需 planYear；专项/临时需 supplierId + plannedDate）。
 */
@Data
@ApiModel(description = "创建审核计划请求")
public class SupplierAuditPlanCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审核类型：年度/专项/临时 */
    @NotNull(message = "审核类型不能为空")
    @ApiModelProperty(value = "审核类型：年度/专项/临时", required = true)
    private String auditType;

    /** 年度计划年份（auditType=年度 时必填） */
    @ApiModelProperty(value = "年度计划年份")
    private Integer planYear;

    /** 专项/临时计划的供应商ID */
    @ApiModelProperty(value = "供应商ID（专项/临时必填）")
    private Long supplierId;

    /** 计划审核日期（专项/临时必填） */
    @ApiModelProperty(value = "计划审核日期（专项/临时必填）")
    private String plannedDate;

    @ApiModelProperty(value = "审核人")
    private String auditor;

    @ApiModelProperty(value = "备注")
    private String remark;
}
