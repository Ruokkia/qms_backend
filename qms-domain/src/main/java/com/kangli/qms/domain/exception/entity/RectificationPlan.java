package com.kangli.qms.domain.exception.entity;

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
 * 整改计划实体 — 对应 qms.rectification_plan 表。
 * <p>与改善措施（improvement_action）区分的独立对象：描述异常整改的整体计划/目标/周期，
 * 下挂具体的改善措施执行项。</p>
 */
@Data
@TableName(value = "rectification_plan", schema = "qms")
public class RectificationPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联异常单ID */
    private Long exceptionId;

    /** 计划编号（后端自动生成 RP+6位序号，可覆盖） */
    private String planNo;

    /** 计划名称 */
    private String planName;

    /** 整改目标 */
    private String objective;

    /** 负责人ID */
    private Long ownerId;

    /** 负责人姓名 */
    private String ownerName;

    /** 计划开始日期 */
    private LocalDate planStartDate;

    /** 计划结束日期 */
    private LocalDate planEndDate;

    /** 状态：待执行/执行中/已完成 */
    private String status;

    private String remark;

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
