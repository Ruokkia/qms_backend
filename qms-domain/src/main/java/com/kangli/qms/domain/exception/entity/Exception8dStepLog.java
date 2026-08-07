package com.kangli.qms.domain.exception.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 8D 步骤留痕实体 — 对应 qms.exception_8d_step_log 表。
 * <p>每次 8D 步骤保存（SAVE）或推进（NEXT_STEP）都记录一条操作快照，支撑审计追溯。</p>
 */
@Data
@TableName(value = "exception_8d_step_log", schema = "qms")
public class Exception8dStepLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联异常单ID */
    private Long exceptionId;

    /** 操作步骤：D1-D8 */
    private String step;

    /** 该步骤填写内容快照 */
    private String stepContent;

    /** 操作类型：SAVE（保存内容）/ NEXT_STEP（推进到下一步） */
    private String operation;

    /** 操作人（真实姓名） */
    private String operator;

    /** 操作时间 */
    private LocalDateTime operatedAt;

    /** 该步骤提交后的审批状态：PENDING_APPROVAL/APPROVED/REJECTED（未配置审批为空） */
    private String approvalStatus;

    /** 审批人姓名 */
    private String approver;

    /** 审批意见（通过/驳回理由） */
    private String approvalComment;

    /** 审批时间 */
    private LocalDateTime approvalTime;

    /** 分公司编码 SZ=深圳 MZ=梅州（数据隔离维度） */
    private String plantCode;

    private String plantName;

    @TableLogic
    private Short isDeleted;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
