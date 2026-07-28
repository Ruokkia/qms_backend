package com.kangli.qms.entity;

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

    /** 供应商名称（关联 qms.supplier，列表/详情回填，非持久化字段） */
    @TableField(exist = false)
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
