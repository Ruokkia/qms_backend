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
 * 首件检验标准模板参数项实体 — 对应 qms.fai_inspection_standard_item 表。
 * <p>标准模板的参数清单，建单时复制到明细表。</p>
 */
@Data
@TableName(value = "fai_inspection_standard_item", schema = "qms")
public class FaiInspectionStandardItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联标准模板主表 fai_inspection_standard.id */
    private Long standardId;
    /** 参数名称 */
    private String paramName;
    /** 参数编码 */
    private String paramCode;
    /** 参数类别：AQL/关键尺寸/性能参数 */
    private String paramCategory;
    /** 标准值 */
    private String standardValue;
    /** 上限 */
    private BigDecimal upperLimit;
    /** 下限 */
    private BigDecimal lowerLimit;
    /** 单位 */
    private String unit;
    /** 是否必检：是/否 */
    private String isRequired;
    /** 排序 */
    private Integer sortOrder;
    /** 是否在首件签名后自动写入 SPC：是/否 */
    private String spcEnabled;
    /** SPC 参数 ID；spcEnabled=是时必须绑定，数值标准由该参数提供 */
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
