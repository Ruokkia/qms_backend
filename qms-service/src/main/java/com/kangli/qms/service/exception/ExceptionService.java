package com.kangli.qms.service.exception;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.service.exception.dto.ExceptionCloseDTO;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.exception.vo.CloseReadinessVO;
import com.kangli.qms.domain.exception.vo.ExceptionAnalysisVO;
import com.kangli.qms.domain.exception.vo.ExceptionDetailVO;
import com.kangli.qms.domain.exception.vo.ExceptionStatsVO;
import com.kangli.qms.domain.supplier.vo.SupplierExceptionSummaryVO;
import com.kangli.qms.domain.exception.vo.QualityRuleCatalogVO;

import java.util.List;

/**
 * M2 异常与整改 Service。
 */
public interface ExceptionService {

    /** 分页查询异常单（支持按整改流程类型 / CAPA 状态筛选） */
    PageResult<ExceptionOrder> list(int page, int size, String severity, String status,
                                       Long supplierId, String sourceType,
                                       String processType, String capaStatus,
                                       String startDate, String endDate);

    /** 异常单详情（含改善措施+验证记录子数组+8D+关联来料） */
    ExceptionDetailVO detail(Long id);

    /** 新增异常单 */
    ExceptionOrder create(ExceptionOrder order);

    /** 根据来料检验记录自动生成异常单 */
    ExceptionOrder createFromMaterialInspection(MaterialInspection inspection, LoginUser loginUser);

    /** 更新异常单 */
    void update(Long id, ExceptionOrder order);

    /** 逻辑删除异常单 */
    void delete(Long id);

    /**
     * 发起整改流程：选择 CAPA / 8D / BOTH，将 process_type 写入并将 capa_status 由「待发起」推进为「进行中」。
     * 仅当 capa_status='待发起' 时可发起；已发起或已闭环的异常单不允许重复发起。
     */
    ExceptionOrder initiate(Long id, String processType);

    /** 异常闭环（增强前置条件：全部整改计划/改善措施完成 + 最新验证通过 + 8D D8完成（若含8D）） */
    void close(Long id, ExceptionCloseDTO dto);

    /**
     * 闭环前置条件检查（逐项返回 PASS/FAIL/NA），供前端实时展示。
     */
    CloseReadinessVO closeReadiness(Long id);

    /**
     * 根据来源记录 ID（如 material_inspection.id）查找已关联的异常单 ID。
     * @return exceptionId 或 null
     */
    Long findExceptionBySourceId(Long sourceId);

    /**
     * 根据已有的来料检验记录 ID，为其创建异常整改单。
     * 仅当该检验记录尚未关联异常单时可创建。
     */
    ExceptionOrder createFromInspectionId(Long inspectionId);

    /** KPI 看板统计 */
    ExceptionStatsVO stats();

    /** 多维度分析 */
    ExceptionAnalysisVO analysis(String dimension);

    /** 公开展示当前来料异常判定、通知、升级及处理措施规则 */
    QualityRuleCatalogVO qualityRules();

    /** 供应商来料不良频次汇总 */
    List<SupplierExceptionSummaryVO> supplierSummary(Long supplierId, String startDate, String endDate,
                                                      Integer minCount);

    /**
     * 重置异常单为最初状态（清空 processType、capaStatus 重置为待发起、status 重置为待整改、closedAt 清空），
     * 同时逻辑删除其关联的改善措施、验证记录、8D 报告、整改计划。
     * 仅「已闭环」状态可重置。
     */
    void reset(Long id);

    /**
     * 审核追溯：聚合该异常单自身（exception_order）+ 改善措施 + 验证记录 + 8D + 整改计划
     * 的全部审计日志，按操作时间倒序返回。
     */
    List<AuditLog> auditTrail(Long id);
}


