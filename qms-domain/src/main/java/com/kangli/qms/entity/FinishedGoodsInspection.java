package com.kangli.qms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
 * 成品入库检验审核实体 — 对应 qms.finished_goods_inspection 表。
 * <p>成品最终检验，含品管审核+管代批准双签流程。</p>
 */
@Data
@TableName(value = "finished_goods_inspection", schema = "qms")
public class FinishedGoodsInspection implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 是否加急：是/否 */
    private String isUrgent;
    /** 品管审核：待审核/已审核/驳回 */
    private String qcReview;
    /** 管代批准：待审核/已审核/驳回 */
    private String mgrApproval;
    private String isValid;
    private String inspectionResult;
    /** 报告编号（唯一） */
    private String reportNo;
    private String inspectionRequestNo;
    private String productionOrderNo;
    private String materialCode;
    private String productName;
    private String modelSpec;
    private String prodBatchOrSn;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private BigDecimal submittedQty;
    private BigDecimal inspectedQty;
    private BigDecimal qualifiedQty;
    private BigDecimal unqualifiedQty;
    private String unit;
    private String inspectorName;
    private String category;
    private String qcReviewer;
    private LocalDateTime qcReviewTime;
    private String mgrRepresentative;
    private LocalDateTime mgrApprovalTime;
    private String isEntrusted;
    private String drugRegNo;
    private String perfTestMethod;
    private String perfSampleBatchNo;

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
