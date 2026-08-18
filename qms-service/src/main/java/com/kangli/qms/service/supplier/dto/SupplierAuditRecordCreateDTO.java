package com.kangli.qms.service.supplier.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 创建审核记录请求。
 */
@Data
@ApiModel(description = "创建审核记录请求")
public class SupplierAuditRecordCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "关联审核计划ID")
    private Long planId;

    @NotNull(message = "供应商ID不能为空")
    @ApiModelProperty(value = "供应商ID", required = true)
    private Long supplierId;

    @NotNull(message = "审核日期不能为空")
    @ApiModelProperty(value = "审核日期 yyyy-MM-dd", required = true)
    private String auditDate;

    @ApiModelProperty(value = "审核人")
    private String auditor;

    @ApiModelProperty(value = "审核摘要")
    private String auditSummary;

    /** 不符合项列表（含分级与照片URL） */
    @ApiModelProperty(value = "不符合项列表")
    private List<FindingItem> findings;

    @Data
    @ApiModel(description = "不符合项录入项")
    public static class FindingItem implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotNull(message = "不符合项级别不能为空")
        @ApiModelProperty(value = "级别：严重/一般/观察项", required = true)
        private String level;

        @NotNull(message = "不符合项描述不能为空")
        @ApiModelProperty(value = "描述", required = true)
        private String description;

        @ApiModelProperty(value = "现场照片相对URL列表")
        private List<String> photoUrls;
    }
}
