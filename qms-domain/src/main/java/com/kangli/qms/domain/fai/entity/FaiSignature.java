package com.kangli.qms.domain.fai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 首件检验电子签名实体 — 对应 qms.fai_signature 表（独立合规表）。
 * <p>仅存 SHA-256 摘要（=SHA256(fai_no|signer_id|signed_at|sign_reason)），不存明文签名。</p>
 */
@Data
@TableName(value = "fai_signature", schema = "qms")
public class FaiSignature implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联首件检验主记录 fai_inspection_record.id */
    private Long faiRecordId;
    /** 签名人ID（登录用户） */
    private String signerId;
    /** 签名人姓名 */
    private String signerName;
    /** 签名类型：检验签/审核签 */
    private String signType;
    /** SHA-256 哈希（仅存摘要，覆盖元数据） */
    private String signatureHash;

    /** 内容绑定哈希（SHA-256）：绑定完整检验记录内容（逐项实际值/判定/标准值/上下限 + 主表结论），
     *  用于读取时完整性复核，满足 21 CFR Part 11 防篡改要求；历史签名行为 NULL */
    private String contentHash;
    /** 签名时间 */
    private LocalDateTime signedAt;
    /** 签名原因 */
    private String signReason;

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
