package com.kangli.qms.domain.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("notification_config")
public class NotificationConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场景编码：EXCEPTION_CREATED / EXCEPTION_CLOSED / ESCALATION_TRIGGERED */
    private String scenarioCode;

    /** 场景名称 */
    private String scenarioName;

    /** 接收角色编码 JSON 数组，如 ["R02","R05"] */
    private String roleCodes;

    /** 严重等级追加角色 JSON 数组，仅 EXCEPTION_CREATED 场景使用 */
    private String severityExtraRoles;

    /** 是否启用：0 禁用 / 1 启用 */
    private Integer enabled;

    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;
}
