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
 * 供应商物料变更单主表实体 — 对应 qms.supplier_material_change 表。
 * <p>覆盖规格/工艺/产地变更的受控管理，批准后触发首件加严检验并通知相关部门。</p>
 */
@Data
@TableName(value = "supplier_material_change", schema = "qms")
public class SupplierMaterialChange implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 变更单号（规则 SMC-{plant}-{yyyyMMdd}-{4位流水}） */
    private String changeNo;

    /** 申请人用户ID */
    private Long applicantId;

    /** 申请人姓名 */
    private String applicant;

    private String supplierCode;
    private String supplierName;
    private String materialCode;
    private String materialName;

    /** 变更类型：SPEC 规格 / PROCESS 工艺 / ORIGIN 产地 */
    private String changeType;

    /** 变更说明 */
    private String changeDesc;

    /** 验证报告（文本+附件URL JSON） */
    private String validationReport;

    /** 风险评估 */
    private String riskAssessment;

    /** 关联的 FAI 检验标准 ID（可空，人工维护后回填，仅追溯） */
    private Long standardId;

    /** 加严子组样本数（SPC 子组大小） */
    private Integer tightenedSubgroupSize;

    /** 是否联动 SPC：0 否 / 1 是 */
    private Short spcEnabled;

    /** 状态：DRAFT / PENDING / APPROVED / REJECTED / VOID */
    private String status;

    /** 驳回原因 */
    private String rejectReason;

    /** 附件 URL JSON */
    private String attachments;

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
