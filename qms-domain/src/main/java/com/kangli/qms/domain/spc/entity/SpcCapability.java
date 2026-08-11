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
 * SPC 过程能力指数实体 — 对应 qms.spc_capability 表。
 * <p>按参数计算的 CP/CPK/PP/PPK/CPU/CPL 及判定结果。</p>
 */
@Data
@TableName(value = "spc_capability", schema = "qms")
public class SpcCapability implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联参数 spc_parameter.id */
    private Long paramId;
    /** 过程能力指数 CP */
    private BigDecimal cp;
    /** 过程能力指数 CPK */
    private BigDecimal cpk;
    /** 过程性能指数 PP */
    private BigDecimal pp;
    /** 过程性能指数 PPK */
    private BigDecimal ppk;
    /** 上能力指数 CPU */
    private BigDecimal cpu;
    /** 下能力指数 CPL */
    private BigDecimal cpl;
    /** 总样本数 */
    private Integer sampleCount;
    /** 子组数 */
    private Integer subgroupCount;
    /** 判定：充足/需改进/不足 */
    private String judgment;
    /** 统计起始时间 */
    private LocalDateTime startTime;
    /** 统计结束时间 */
    private LocalDateTime endTime;

    // ---- 维度隔离列 ----
    /** 关联维度：PRODUCT/MATERIAL，为空表示全局基线 */
    private String itemType;
    /** 关联产品/物料代码，为空表示全局基线 */
    private String itemCode;

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
