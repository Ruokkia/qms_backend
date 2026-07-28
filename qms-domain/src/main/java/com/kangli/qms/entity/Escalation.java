package com.kangli.qms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * 供应商升级记录实体 — 对应 qms.escalation 表。
 * <p>高频问题供应商自动升级，触发加密审核/暂停供货/专项CAPA。</p>
 */
@Data
@TableName(value = "escalation", schema = "qms")
public class Escalation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 供应商ID */
    private Long supplierId;

    /** 供应商编号（来源于来料检验记录） */
    private String supplierCode;

    /** 供应商名称（来源于来料检验记录） */
    private String supplierName;

    /** 重复发生的物料代码 */
    private String materialCode;

    /** 升级原因 */
    private String escalationReason;

    /** 关联异常单ID列表（逗号分隔） */
    private String relatedExceptionIds;

    /** 升级动作：加密审核/暂停供货/专项CAPA */
    private String escalationAction;

    /** 状态：PENDING_REVIEW/ACTIVE/REJECTED/CLOSED */
    private String status;

    /** 流程阶段：PENDING_REVIEW/PLAN/EXECUTION/VERIFICATION/PENDING_CLOSE_APPROVAL/CLOSED/REJECTED */
    private String processStage;

    /** 审核通过后制定的升级措施与责任计划 */
    private String actionPlan;
    private String ownerName;
    private LocalDate dueDate;

    /** 措施执行记录 */
    private String executionRecord;
    private String executedBy;
    private LocalDateTime executedAt;

    /** 效果验证 */
    private String verificationResult;
    private String verificationEvidence;
    private String verifiedBy;
    private LocalDateTime verifiedAt;

    /** 质量经理关闭审批 */
    private String closeReason;
    private String closedBy;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    private String remark;

    /** 升级审核意见 */
    private String reviewOpinion;

    /** 审核人 */
    private String reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    // ---- 电子签名 ----
    private String signatureUser;
    private LocalDateTime signatureTime;
    private String signatureReason;

    // ---- 系统扩展列 ----
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
