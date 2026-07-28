package com.kangli.qms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志实体 — 对应 qms.audit_log 表。
 * <p>记录所有业务表 CUD 操作，满足 GMP 审计追溯要求。</p>
 */
@Data
@TableName(value = "audit_log", schema = "qms")
public class AuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作表名 */
    private String tableName;

    /** 行记录ID */
    private Long recordId;

    /** 操作类型：CREATE / UPDATE / DELETE */
    private String operationType;

    /** 变更前数据（JSONB） */
    private String beforeData;

    /** 变更后数据（JSONB） */
    private String afterData;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名（冗余） */
    private String operatorName;

    /** 分公司编码 */
    private String plantCode;

    /** 操作IP */
    private String ipAddress;

    /** 操作时间 */
    private LocalDateTime operationTime;

    /** 操作原因/备注 */
    private String reason;

    // ---- 系统扩展列 ----
    private String createdBy;
    private String updatedBy;
    private Short isDeleted;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
