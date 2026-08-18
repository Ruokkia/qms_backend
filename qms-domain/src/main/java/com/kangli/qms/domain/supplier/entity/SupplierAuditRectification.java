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
 * 供应商现场审核不符合项整改跟踪 — 对应 qms.supplier_audit_rectification 表。
 */
@Data
@TableName(value = "supplier_audit_rectification", schema = "qms")
public class SupplierAuditRectification implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long findingId;
    /** 整改措施 */
    private String measure;
    private LocalDate dueDate;
    /** 整改责任人 */
    private String owner;
    /** 验证结果：通过/不通过 */
    private String verifyResult;
    private String verifiedBy;
    /** 闭环时间 */
    private LocalDateTime closedAt;

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
