package com.kangli.qms.domain.exception.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 异常单实体 — 对应 qms.exception_order 表。
 * <p>驱动 8D/CAPA 整改流程的核心工单。</p>
 */
@Data
@TableName(value = "exception_order", schema = "qms")
public class ExceptionOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 异常单号（唯一，格式 EX-年月日-序号） */
    private String exceptionNo;

    /** 来源类型：来料不良/制程不良/审核问题/客户投诉/重复问题 */
    private String sourceType;

    /** 来源记录ID */
    private Long sourceId;

    /** 严重等级：严重/一般 */
    private String severity;

    /** 状态：待整改/整改中/待验证/已闭环 */
    private String status;

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称（创建时直接存储，即使 supplier_id 查找失败也可展示） */
    private String supplierName;

    /** 关联工单ID */
    private Long workOrderId;

    /** 物料代码 */
    private String materialCode;

    /** 不良描述（自由文本） */
    private String defectDesc;

    /** 不良数量 */
    private BigDecimal defectQty;

    /** 总数量 */
    private BigDecimal totalQty;

    /** 处理人ID */
    private Long handlerId;

    /** 审核人/复核人ID */
    private Long reviewerId;

    /** 8D/CAPA 状态：待发起/进行中/已完成 */
    private String capaStatus;

    /** 整改流程类型：CAPA(改善措施+验证) / 8D(八步报告) / BOTH(两者) / NULL(待选择) */
    private String processType;

    /**
     * CAPA 治理阶段（BOTH 模式专用）：
     * INITIATE / ROOT_CAUSE_APPROVED / MEASURES_APPROVED / CLOSED
     * 8D 与 CAPA 交错推进：D4→D5 需通过根因审批，D5→D6 需通过措施审批。
     */
    @TableField("capa_phase")
    private String capaPhase;

    /** 整改截止日期 */
    private LocalDate deadline;

    /** 自动分级命中原因 */
    private String ruleReason;

    /** 通知等级：严重/提醒 */
    private String notificationLevel;

    /** 首次响应截止时间 */
    private LocalDateTime responseDeadline;

    /** 同供应商、同物料近30天不合格批次 */
    @TableField("repeat_count_30_days")
    private Integer repeatCount30Days;

    /** 同供应商、同物料近90天不合格批次 */
    @TableField("repeat_count_90_days")
    private Integer repeatCount90Days;

    /** 重复问题识别键 */
    private String problemFingerprint;

    /** 来源来料记录的处理方式 */
    private String handlingMethod;

    /** 闭环时间 */
    private LocalDateTime closedAt;

    private String remark;

    // ---- 电子签名 ----
    private String signatureUser;
    private LocalDateTime signatureTime;
    private String signatureReason;

    // ---- 发起整改流程责任人 ----
    /** 整改责任人 ID（自动触发时留空，由相关部门在「发起整改」时从已有人员中选择填写） */
    @TableField("owner_id")
    private Long ownerId;

    /** 整改责任人姓名（冗余存储，便于列表/详情直接展示，与 initiatedBy 发起操作人区分） */
    @TableField("owner_name")
    private String ownerName;

    /** 发起整改流程的责任人姓名（点击「发起整改」的人，与 updatedBy 区分） */
    @TableField("initiated_by")
    private String initiatedBy;

    /** 发起整改流程的时间 */
    @TableField("initiated_at")
    private LocalDateTime initiatedAt;

    /** 发起整改流程的责任人用户ID（用于通知推送） */
    @TableField("initiated_by_user_id")
    private Long initiatedByUserId;

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
