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

    /** 当前步骤：D1-D8 */
    private String currentStep;

    /** D1 团队成立 */
    private String d1Team;

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
