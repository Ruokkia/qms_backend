package com.kangli.qms.domain.production.entity;

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
 * 生产维修记录实体 — 对应 qms.production_repair 表。
 * <p>全品类生产不良数据归集与分析，不参与来料→成品主追溯链路。</p>
 * <p>说明：process（生产工序）为自由文本；repair_status（状态：）为自由文本，不建枚举；
 * repair_done（0未维修/1已维修）对应 Excel「维修状态」0/1，为非冗余数值字段。</p>
 */
@Data
@TableName(value = "production_repair", schema = "qms")
public class ProductionRepair implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 审核状态：待审核/已审核/驳回 */
    private String auditStatus;
    /** 表格名称 */
    private String formName;
    /** 维修编号（同分公司唯一） */
    private String repairNo;
    private String productNo;
    private String productName;
    private String specModel;
    private String workOrderNo;
    private String productBatchOrSn;
    /** 生产工序（自由文本，自然语言描述） */
    private String process;
    private BigDecimal defectQty;
    private String defectPhenomenon;
    /** 历史 Excel 自由文本不良代码（不作为统计维度） */
    private String defectCode;
    private LocalDate sendRepairDate;
    private LocalDate repairDate;
    private String repairJudgmentResult;
    /** 状态：文本（待维修未提交/已提交待审核/已审核…，自由文本不建枚举） */
    private String repairStatus;
    private String repairRecord;
    private String sendRepairer;
    private String repairer;
    private String auditor;
    private LocalDate auditDate;
    private String remark;

    /** 维修状态（数值）：0=未维修 1=已维修，对应 Excel「维修状态」 */
    private Integer repairDone;
    /** 表格编号（Excel「表格编号」列） */
    private String formNo;

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
