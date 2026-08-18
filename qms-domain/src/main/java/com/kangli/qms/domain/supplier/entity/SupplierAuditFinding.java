package com.kangli.qms.domain.supplier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 供应商现场审核不符合项 — 对应 qms.supplier_audit_finding 表。
 */
@Data
@TableName(value = "supplier_audit_finding", schema = "qms")
public class SupplierAuditFinding implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;
    private Long supplierId;
    private String supplierCode;
    /** 不符合项级别：严重/一般/观察项 */
    private String level;
    private String description;
    /** 现场照片相对 URL，逗号分隔 */
    private String photoUrls;
    /** 状态：待整改/整改中/待验证/已闭环 */
    private String status;

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
