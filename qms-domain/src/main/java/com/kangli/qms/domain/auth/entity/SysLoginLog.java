package com.kangli.qms.domain.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志实体 — 对应 qms.sys_login_log 表。
 * <p>日志表仅追加不修改，不带逻辑删除与乐观锁（按 DDL 设计）。</p>
 */
@Data
@TableName(value = "sys_login_log", schema = "qms")
public class SysLoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（登录失败时可能为空） */
    private Long userId;

    /** 登录账号 */
    private String account;

    /** 分公司编码 */
    private String plantCode;

    /** 分公司名称 */
    private String plantName;

    /** 登录IP */
    private String loginIp;

    /** 登录状态：成功 / 失败 / 锁定 */
    private String loginStatus;

    /** 失败原因 */
    private String failReason;

    /** 登录时间 */
    private LocalDateTime loginTime;

    // ---- 系统扩展列（日志表保留，但不更新）----
    private String createdBy;
    private String updatedBy;
    private Short isDeleted;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
