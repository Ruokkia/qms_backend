package com.kangli.qms.service.supplier;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.domain.supplier.entity.SupplierAuditFinding;
import com.kangli.qms.domain.supplier.entity.SupplierAuditPlan;
import com.kangli.qms.domain.supplier.entity.SupplierAuditRecord;
import com.kangli.qms.domain.supplier.vo.SupplierAuditAuditorVO;
import com.kangli.qms.domain.supplier.vo.SupplierAuditReportVO;
import com.kangli.qms.service.supplier.dto.SupplierAuditPlanCreateDTO;
import com.kangli.qms.service.supplier.dto.SupplierAuditRecordCreateDTO;
import com.kangli.qms.service.supplier.dto.SupplierAuditRectifyDTO;

import java.util.List;

/**
 * 供应商现场审核业务接口。
 */
public interface SupplierAuditService {

    /** 制定计划：年度自动按风险等级分配频次 / 专项 / 临时 */
    List<SupplierAuditPlan> createPlan(SupplierAuditPlanCreateDTO dto);

    /** 计划分页列表（plantCode 隔离，supplierId 用于联动供应商档案） */
    PageResult<SupplierAuditPlan> listPlans(int page, int size, Long supplierId, String auditType, String status, String keyword);

    /** 审核记录分页列表（supplierId 用于联动供应商档案） */
    PageResult<SupplierAuditRecord> listRecords(int page, int size, Long supplierId, String keyword);

    /** 不符合项分页列表（按状态过滤，supplierId 用于联动供应商档案，plantCode 隔离） */
    PageResult<SupplierAuditFinding> listFindings(int page, int size, Long supplierId, String status, String keyword);

    /** 新建审核记录 + 不符合项（含照片URL） */
    SupplierAuditRecord createRecord(SupplierAuditRecordCreateDTO dto);

    /** 提交整改措施（状态 待整改→整改中） */
    void rectify(Long findingId, SupplierAuditRectifyDTO dto);

    /** 验证结果 + 闭环（状态 待验证→已闭环，写 closedAt） */
    void verify(Long findingId, SupplierAuditRectifyDTO dto);

    /** 审核报告（聚合记录 + 不符合项 + 整改状态） */
    SupplierAuditReportVO getReport(Long recordId);

    /** 审核人下拉选项（当前分公司启用用户） */
    List<SupplierAuditAuditorVO> listAuditors();
}
