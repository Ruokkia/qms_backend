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
 * 供应商现场审核计划 — 对应 qms.supplier_audit_plan 表。
 */
@Data
@TableName(value = "supplier_audit_plan", schema = "qms")
public class SupplierAuditPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    /** 审核类型：年度/专项/临时 */
    private String auditType;
    private Integer planYear;
    /** 审核频次：如 一年2次/一年1次/两年1次 */
    private String frequency;
    private LocalDate plannedDate;
    /** 状态：草稿/已排期/已完成 */
    private String status;
    private String auditor;
    private String remark;

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
