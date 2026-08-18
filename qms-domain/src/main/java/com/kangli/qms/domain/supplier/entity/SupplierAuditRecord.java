package com.kangli.qms.domain.supplier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商现场审核记录 — 对应 qms.supplier_audit_record 表（一次现场审核）。
 */
@Data
@TableName(value = "supplier_audit_record", schema = "qms")
public class SupplierAuditRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;
    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    /** 审核报告编号（格式 SA-年月日-序号） */
    private String reportNo;
    private LocalDate auditDate;
    private String auditor;
    private String auditSummary;
    /** 高风险物料额外资料要求（若供应商涉及高风险物料，自动汇总） */
    private String extraRequirement;

    private String plantCode;
    private String plantName;
    private String createdBy;
    private String updatedBy;

    @TableLogic
    private Short isDeleted;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
