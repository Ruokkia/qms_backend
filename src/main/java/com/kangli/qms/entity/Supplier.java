package com.kangli.qms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 供应商基础实体 — 对应 qms.supplier 表。
 */
@Data
@TableName(value = "supplier", schema = "qms")
public class Supplier implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String supplierCode;
    private String supplierName;
    private String contactPerson;
    private String contactPhone;
    private String address;
    /** 风险等级：高/中/低 */
    private String riskLevel;
    /** 状态：启用/停用 */
    private String status;
    private String remark;

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
