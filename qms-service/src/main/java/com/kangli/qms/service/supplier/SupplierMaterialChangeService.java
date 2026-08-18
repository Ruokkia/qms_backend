package com.kangli.qms.service.supplier;

import com.kangli.qms.common.PageResult;
import com.kangli.qms.service.supplier.dto.SupplierMaterialChangeApproveDTO;
import com.kangli.qms.service.supplier.dto.SupplierMaterialChangeCreateDTO;
import com.kangli.qms.service.supplier.dto.SupplierMaterialChangeDetailVO;
import com.kangli.qms.service.supplier.dto.SupplierMaterialChangeRejectDTO;

/**
 * 供应商物料变更管理服务。
 * <p>覆盖「变更申请 → 质量/采购/研发联合审批（并行会签 + 一票否决）→ 首批加严检验」受控流程。</p>
 */
public interface SupplierMaterialChangeService {

    /**
     * 提交变更申请：创建主单（PENDING）+ 三条审批记录（质量/采购/研发），并通知各审批人。
     */
    SupplierMaterialChangeDetailVO create(SupplierMaterialChangeCreateDTO dto);

    /**
     * 分页查询变更单列表（按厂区隔离）。
     */
    PageResult<SupplierMaterialChangeDetailVO> page(int page, int size, String keyword, String status, String changeType);

    /**
     * 变更单详情（聚合审批记录）。
     */
    SupplierMaterialChangeDetailVO detail(Long id);

    /**
     * 审批通过（当前角色审批人操作）。
     */
    void approve(Long id, SupplierMaterialChangeApproveDTO dto);

    /**
     * 审批驳回（当前角色审批人操作，一票否决）。
     */
    void reject(Long id, SupplierMaterialChangeRejectDTO dto);

    /**
     * 作废变更单（仅审批中/草稿可作废，申请人或管理员操作）。
     */
    void voidChange(Long id);

    /**
     * 我的审批列表（当前登录用户为审批人且待审批的记录）。
     */
    PageResult<SupplierMaterialChangeDetailVO> myApprovals(int page, int size);
}
