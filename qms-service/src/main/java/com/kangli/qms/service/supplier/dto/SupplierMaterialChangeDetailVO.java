package com.kangli.qms.service.supplier.dto;

import com.kangli.qms.domain.supplier.entity.SupplierMaterialChange;
import com.kangli.qms.domain.supplier.entity.SupplierMaterialChangeApproval;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 供应商物料变更详情 VO（主单 + 审批记录聚合）。
 */
@Data
public class SupplierMaterialChangeDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变更单主单 */
    private SupplierMaterialChange change;

    /** 联合审批记录（质量/采购/研发） */
    private List<SupplierMaterialChangeApproval> approvals;

    /** 当前登录用户可审批的角色（若当前用户为某条 PENDING 记录的审批人，返回其角色；否则为 null） */
    private String myApprovalRole;
}
