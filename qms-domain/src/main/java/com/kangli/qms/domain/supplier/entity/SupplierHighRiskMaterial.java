package com.kangli.qms.domain.supplier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 供应商-高风险物料关联。标记某供应商涉及的高风险物料，用于审核附加要求。
 */
@Data
@TableName(value = "supplier_high_risk_material", schema = "qms")
public class SupplierHighRiskMaterial implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long supplierId;
    private String supplierCode;
    private String supplierName;
    private Long materialId;
    private String materialCode;
    private String materialName;
    /** 额外资料要求（冗余自 high_risk_material，便于展示） */
    private String extraRequirement;

    private String plantCode;
    private String plantName;
    private String createdBy;

    @TableLogic
    private Short isDeleted;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
}
