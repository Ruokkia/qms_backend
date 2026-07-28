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
 * SPC 采样明细实体 — 对应 qms.spc_sample 表。
 * <p>子组下的单条样本实测值。</p>
 */
@Data
@TableName(value = "spc_sample", schema = "qms")
public class SpcSample implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联子组 spc_subgroup.id */
    private Long subgroupId;
    /** 样本序号（1~n） */
    private Integer sampleNo;
    /** 样本实测值 */
    private BigDecimal sampleValue;

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
