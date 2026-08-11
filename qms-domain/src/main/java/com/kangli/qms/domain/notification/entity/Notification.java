package com.kangli.qms.domain.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.kangli.qms.domain.notification.handler.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知实体 — 对应 qms.notification 表。
 * <p>异常单创建/状态变更/升级触发时自动写入，支持站内信列表与未读数。</p>
 */
@Data
@TableName(value = "notification", schema = "qms")
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人ID */
    private Long userId;

    /** 通知类型：EXCEPTION_CREATED / EXCEPTION_STATUS_CHANGED / ESCALATION_TRIGGERED */
    private String type;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 通知等级：严重/警告/提醒 */
    private String level;

    /** 业务类型：EXCEPTION_ORDER / ESCALATION */
    private String businessType;

    /** 业务ID */
    private Long businessId;

    /** 是否已读：0=未读 1=已读 */
    private Short isRead;

    /** 读取时间 */
    private LocalDateTime readAt;

    /** 扩展数据（JSONB）：存储跳转参数和摘要信息（异常单号、供应商、严重等级等） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String extraData;

    /** 通知过期时间（NULL 表示永不过期） */
    private LocalDateTime expireAt;

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
