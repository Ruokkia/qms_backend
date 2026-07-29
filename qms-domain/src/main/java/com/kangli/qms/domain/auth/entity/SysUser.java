package com.kangli.qms.domain.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户实体 — 对应 qms.sys_user 表。
 */
@Data
@TableName(value = "sys_user", schema = "qms")
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号（全局唯一） */
    private String account;

    /** BCrypt 密码哈希 */
    private String passwordHash;

    /** 真实姓名 */
    private String realName;

    /** 角色编码（关联 sys_role.role_code） */
    private String roleCode;

    /** 认证版本；角色、权限、状态或密码变化后递增以使旧会话失效 */
    private Integer authVersion;

    /** 分公司编码 SZ=深圳 MZ=梅州 */
    private String plantCode;

    /** 分公司名称 */
    private String plantName;

    /** 状态 1=启用 0=禁用 */
    private Short status;

    /** 连续登录失败次数（Redis 兜底） */
    private Integer loginFailCount;

    /** 锁定截止时间（NULL=未锁定） */
    private LocalDateTime lockedUntil;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    // ---- 系统扩展列 ----
    private String createdBy;
    private String updatedBy;

    @TableLogic
    private Short isDeleted;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
