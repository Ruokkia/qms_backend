package com.kangli.qms.entity;

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
 * 首件检验参数明细实体 — 对应 qms.fai_inspection_item 表。
 * <p>从标准模板复制生成，actual_value 录入后由业务层自动判定 result。</p>
 */
@Data
@TableName(value = "fai_inspection_item", schema = "qms")
public class FaiInspectionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联首件检验主记录 fai_inspection_record.id */
    private Long faiRecordId;
    /** 关联标准参数项 fai_inspection_standard_item.id（用于标准值同步追踪） */
    private Long standardItemId;
    /** 参数名称 */
    private String paramName;
    /** 参数编码（SPC 联动分组依据） */
    private String paramCode;
    /** 参数类别：AQL/关键尺寸/性能参数（复制自标准模板） */
    private String paramCategory;
    /** 标准值（上下限均为空时做精确匹配） */
    private String standardValue;
    /** 上限 */
    private BigDecimal upperLimit;
    /** 下限 */
    private BigDecimal lowerLimit;
    /** 实际值（录入后用于判定） */
    private BigDecimal actualValue;
    /** 单位 */
    private String unit;
    /** 判定：待判定/合格/不合格 */
    private String result;
    /** 排序 */
    private Integer sortOrder;
    /** 是否在首件签名后自动写入 SPC：是/否 */
    private String spcEnabled;
    /** 创建首件时从标准项复制的 SPC 参数引用 */
    private Long spcParameterId;

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
