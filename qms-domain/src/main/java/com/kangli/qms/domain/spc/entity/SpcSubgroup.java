package com.kangli.qms.domain.spc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPC 子组实体 — 对应 qms.spc_subgroup 表。
 * <p>一组同条件下采集的样本，计算均值/极差/标准差，是控制图与能力分析的基本单元。</p>
 */
@Data
@TableName(value = "spc_subgroup", schema = "qms")
public class SpcSubgroup implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联参数 spc_parameter.id */
    private Long paramId;
    /** 子组编号 SG-{plant}-{param}-{yyyyMMdd}-{4位流水} */
    private String subgroupNo;
    /** 样本数 */
    private Integer sampleCount;
    /** 子组均值 X̄ */
    private BigDecimal meanValue;
    /** 子组极差 R */
    private BigDecimal rangeValue;
    /** 子组标准差 s */
    private BigDecimal stdDev;
    /** 采样时间 */
    private LocalDateTime sampleTime;
    /** 来源：手动录入/首件导入/自动采集 */
    private String sourceType;
    /** 关联首件记录 id（首件导入时必填） */
    private Long faiRecordId;
    /** 来源工单号 */
    private String workOrderNo;
    /** 来源批次号 */
    private String batchNo;
    /** 来源物料代码（冗余兼容列：物料场景与 itemCode 同步） */
    private String materialCode;
    /** 来源物料名称 */
    private String materialName;
    /** 分类：PRODUCT(产品) / MATERIAL(物料) */
    private String itemType;
    /** 产品/物料代码（随 itemType 取值，控制图关联维度） */
    private String itemCode;
    /** 统一工序库编码 */
    private String processCode;
    /** 参数编码快照 */
    private String paramCode;
    /** 标准单位快照 */
    private String unit;
    /** 首件标准版本快照 */
    private Integer standardVersion;
    /** 子组状态：待补样本/已完成；仅已完成可参与 SPC 统计 */
    private String subgroupStatus;

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
