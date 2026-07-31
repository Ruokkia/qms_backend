package com.kangli.qms.domain.incoming.entity;

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
 * 物料检验入库审核实体 — 对应 qms.material_inspection 表。
 * <p>来料批次质量数据，含审核签名流程，material_batch_no 为全链路追溯核心键。</p>
 */
@Data
@TableName(value = "material_inspection", schema = "qms")
public class MaterialInspection implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String processNo;
    private String formVersion;
    /** 是否客供料：是/否 */
    private String isCustomerSupplied;
    private String memo;
    private String materialCategory;
    /** 是否有效：是/否 */
    private String isValid;
    /** 审核状态：待审核/已审核/驳回 */
    private String reviewStatus;
    /** 签名状态：已签/未签 */
    private String signatureStatus;
    /** 是否急料：是/否 */
    private String isUrgent;
    private String dataRecordFlag;
    private String isInvalid;
    private String reportGenerated;
    /** 记录编号（唯一） */
    private String recordNo;
    private String purchaseOrder;
    private String inboundNo;
    private String inspectionRequestNo;
    private String mesInspectionNo;
    private LocalDate inspectionDate;
    private LocalDate judgementDate;
    private String inspector;
    /** 检验结果：合格/不合格 */
    private String inspectionResult;
    private String supplierName;
    private String materialCode;
    private String materialName;
    private String specModel;
    /** 物料批号（追溯核心键） */
    private String materialBatchNo;
    /** 物料条码（SN 级追溯标识，全局唯一） */
    private String materialBarcode;
    private BigDecimal qualifiedQty;
    private BigDecimal unqualifiedQty;
    private BigDecimal submittedQty;
    private BigDecimal lossQty;
    private String unit;
    /** 不合格描述（自由文本） */
    private String defectDesc;
    /** 处理方式：退货/挑选/特采/报废 */
    private String handlingMethod;
    private String unqualifiedFinalStatus;
    private String unqualifiedReview;
    private String unqualifiedReviewNo;
    private String inspectionCategory;
    private LocalDate arrivalDate;
    private String receivingNo;
    private String poLineNo;
    private String receivingLineNo;
    private Integer shelfLifeDays;
    private String reinspectRemark;
    private String judge;
    private LocalDate inspectionEndDate;
    private String reviewer;
    private LocalDate reviewDate;
    private String submitter;
    private LocalDate submitDate;
    private String supplierCode;
    private String remark;
    private String extId;
    private String lastModifiedBy;

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
