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
 * SPC 控制限实体 — 对应 qms.spc_control_limit 表。
 * <p>按参数与控制图类型计算的 X̄/R/s 图上下控制限与中心线。</p>
 */
@Data
@TableName(value = "spc_control_limit", schema = "qms")
public class SpcControlLimit implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联参数 spc_parameter.id */
    private Long paramId;
    /** 控制图类型：Xbar-R / Xbar-s */
    private String chartType;
    /** X̄ 图上控制限 */
    private BigDecimal xbarUcl;
    /** X̄ 图中心线 */
    private BigDecimal xbarCl;
    /** X̄ 图下控制限 */
    private BigDecimal xbarLcl;
    /** R 图上控制限 */
    private BigDecimal rUcl;
    /** R 图中心线 */
    private BigDecimal rCl;
    /** R 图下控制限 */
    private BigDecimal rLcl;
    /** s 图上控制限 */
    private BigDecimal sUcl;
    /** s 图中心线 */
    private BigDecimal sCl;
    /** s 图下控制限 */
    private BigDecimal sLcl;
    /** 用于计算的子组数 */
    private Integer subgroupCount;
    /** 计算时间 */
    private LocalDateTime calcDate;

    // ---- 系统扩展列 ----
    private String plantCode;
    private String plantName;

    /** 关联维度：PRODUCT/MATERIAL，为空表示全局基线 */
    private String itemType;
    /** 关联产品/物料代码，为空表示全局基线 */
    private String itemCode;

    private String createdBy;
    private String updatedBy;

    @TableLogic
    private Short isDeleted;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
