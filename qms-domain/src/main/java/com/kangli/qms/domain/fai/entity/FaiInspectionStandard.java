package com.kangli.qms.domain.fai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 首件检验标准模板主表实体 — 对应 qms.fai_inspection_standard 表。
 * <p>按 物料+工序 维护，最新激活版本用于自动建单。</p>
 */
@Data
@TableName(value = "fai_inspection_standard", schema = "qms")
public class FaiInspectionStandard implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 物料代码 */
    private String materialCode;
    /** 物料名称（冗余，便于展示） */
    private String materialName;
    /** 分类：PRODUCT(产品)/MATERIAL(物料) */
    private String itemType;
    /** 产品/物料代码（随 itemType 取值，产品模式取产品代码，物料模式取物料代码） */
    private String itemCode;
    /** 产品/物料名称（随 itemType 取值） */
    private String itemName;
    /** 产品/物料条码（追溯标识） */
    private String itemBarcode;

    /** 工序：装配/焊接/检测 */
    private String processName;
    private String processCode;
    /** 模板版本号（同一 物料+工序 可有多个版本） */
    private Integer stdVersion;
    /** 是否激活：是/否 */
    private String isActive;
    /** 备注 */
    private String remark;

    /** 生效日期（ECN 变更/药监审计） */
    private java.time.LocalDate effectiveDate;

    /** 变更备注（ECN 变更/药监审计） */
    private String changeRemark;

    /** 最近复审时间（P3：定期复审提醒） */
    private LocalDateTime lastReviewedAt;

    /** 复审间隔天数，默认 90 天（P3：定期复审提醒） */
    private Integer reviewIntervalDays;

    /** 引用状态（0=未引用，1=已引用）（P3：执行情况统计） */
    @TableField("usage_count")
    private Integer usageStatus;

    /** 最近引用时间（P3：执行情况统计） */
    private LocalDateTime lastUsedAt;

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
