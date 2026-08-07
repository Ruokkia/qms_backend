package com.kangli.qms.domain.exception.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.kangli.qms.domain.exception.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 8D/CAPA 报告实体 — 对应 qms.exception_8d 表。
 * <p>1 个异常单对应 1 份 8D 报告（D1-D8）。</p>
 */
@Data
@TableName(value = "exception_8d", schema = "qms")
public class Exception8d implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联异常单ID */
    private Long exceptionId;

    /** 当前步骤：8D 为 D0-D8；CAPA 流程当前阶段另见 capaCurrentStep */
    private String currentStep;

    /** D0 质量部发起说明（立案情由 / 不良现象概述） */
    private String d0Symptom;

    /** D0 发起责任人（质量部发起者姓名） */
    private String d0Initiator;

    /** D0 发起时间 */
    private LocalDateTime d0InitiateTime;

    /** D1 团队成立（JSON 数组：成员姓名列表） — 向后兼容，姓名逗号串供列表展示 */
    private String d1Team;

    /** D1 团队成员结构化列表（JSONB：[{userId, realName, roleCode}]），负责人自行组建提交 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String d1Members;

    /** CAPA 负责人姓名列表（JSON 数组字符串，与 8D 团队对称指派） */
    private String capaOwner;

    /** 当前阶段审批状态：DRAFT/SUBMITTED/PENDING_APPROVAL/APPROVED/REJECTED */
    private String stepStatus;

    /** CAPA 流程当前阶段：C1-C4（选 CAPA 或 8D+CAPA 时维护） */
    private String capaCurrentStep;

    /** D2 问题描述（5W2H） */
    private String d2ProblemDesc;

    /** D3 临时遏制措施 */
    private String d3Containment;

    /** D4 根本原因分析 */
    private String d4RootCause;

    /** D5 纠正措施 */
    private String d5Corrective;

    /** D6 实施与验证 */
    private String d6Implementation;

    /** D7 预防措施 */
    private String d7Preventive;

    /** D8 团队表彰/闭环总结 */
    private String d8Closure;

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
