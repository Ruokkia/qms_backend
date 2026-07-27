package com.kangli.qms.entity;

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
 * 验证记录实体 — 对应 qms.verification_record 表。
 * <p>异常整改效果验证，闭环前置条件。</p>
 */
@Data
@TableName(value = "verification_record", schema = "qms")
public class VerificationRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联异常单ID */
    private Long exceptionId;

    /** 验证方式：供应商自证/内部确认/连续N批 */
    private String verifyType;

    /** 验证结果：通过/不通过 */
    private String result;

    /** 验证人ID */
    private Long verifierId;

    /** 验证人姓名 */
    private String verifierName;

    /** 验证日期 */
    private LocalDate verifyDate;

    /** 验证证据 */
    private String evidence;

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
