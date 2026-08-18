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
 * 供应商物料变更联合审批记录实体 — 对应 qms.supplier_material_change_approval 表。
 * <p>质量/采购/研发三角色并行会签，任一驳回则主单驳回（一票否决）。</p>
 */
@Data
@TableName(value = "supplier_material_change_approval", schema = "qms")
public class SupplierMaterialChangeApproval implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 变更单ID */
    private Long changeId;

    /** 审批角色：QUALITY 质量 / PURCHASE 采购 / RD 研发 */
    private String approvalRole;

    /** 审批人姓名 */
    private String approver;

    /** 审批人用户ID */
    private Long approverId;

    /** 审批状态：PENDING / APPROVED / REJECTED / CANCELLED */
    private String approvalStatus;

    /** 审批意见 */
    private String opinion;

    /** 审批时间 */
    private LocalDateTime approvedAt;

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
