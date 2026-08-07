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
 * 异常整改阶段级审批配置实体 — 对应 qms.exception_approval_config 表。
 * <p>8D（D0-D8）与 CAPA（C1-C4）共用，按阶段配置是否需审批及审批角色。</p>
 */
@Data
@TableName(value = "exception_approval_config", schema = "qms")
public class ExceptionApprovalConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流程维度：8D / CAPA */
    private String processFlow;

    /** 阶段码：8D 为 D0-D8，CAPA 为 C1-C4 */
    private String stage;

    /** 阶段中文名（展示用） */
    private String stageName;

    /** 是否需审批：0=否 1=是 */
    private Short needApproval;

    /** 审批角色：R04=质量工程师 R06=质量经理 */
    private String approverRole;

    /** 是否默认配置（全局默认一套） */
    private Short isDefault;

    // ---- 系统扩展列 ----
    private String plantCode;
    private String plantName;

    @TableLogic
    private Short isDeleted;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
