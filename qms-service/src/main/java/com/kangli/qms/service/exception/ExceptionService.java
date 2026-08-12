package com.kangli.qms.service.exception;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.service.exception.dto.ExceptionCloseDTO;
import com.kangli.qms.service.exception.dto.ExceptionInitiateDTO;
import com.kangli.qms.service.exception.dto.ExceptionUpdateDTO;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.exception.vo.CapaPhaseApprovalReadinessVO;
import com.kangli.qms.domain.exception.vo.CloseReadinessVO;
import com.kangli.qms.domain.exception.vo.ExceptionAnalysisVO;
import com.kangli.qms.domain.exception.vo.ExceptionDetailVO;
import com.kangli.qms.domain.exception.vo.ExceptionStatsVO;
import com.kangli.qms.domain.supplier.vo.SupplierExceptionSummaryVO;
import com.kangli.qms.domain.exception.vo.QualityRuleCatalogVO;
import com.kangli.qms.domain.exception.vo.ExceptionSourceOptionVO;
import com.kangli.qms.domain.exception.vo.ExceptionUserOptionVO;

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

    /**
     * 异常单「选择源头记录」聚合查询。
     * 按来源类型从不同源头库查询并统一返回精简 VO（id/物料编码/物料名称/批号/供应商/工单号）。
     * <ul>
     *   <li>sourceType=material（来料不良）：查已入库来料检验记录</li>
     *   <li>sourceType=fai（首件不良）：查首件检验记录</li>
     *   <li>sourceType=finished（成品不良）：查成品检验记录</li>
     *   <li>sourceType=complaint/process/other（B组）：模糊搜索来料+成品记录（放宽物料编码搜索）</li>
     * </ul>
     *
     * @param sourceType 来源类型（见上）
     * @param keyword    模糊关键词（物料编码/物料名称/批号/供应商），可空
     * @param page       页码（从1开始）
     * @param size       每页条数（上限100）
     */
    PageResult<ExceptionSourceOptionVO> listSourceOptions(String sourceType, String keyword, int page, int size);

    /** 根据来料检验记录自动生成异常单 */
    ExceptionOrder createFromMaterialInspection(MaterialInspection inspection, LoginUser loginUser);

    /** 根据首件检验不合格记录自动生成异常单（避免重复创建） */
    ExceptionOrder createFromFai(FaiInspectionRecord record, LoginUser loginUser);

    /** 根据成品入库检验不合格记录自动生成异常单（避免重复创建，异常来源=成品不良） */
    ExceptionOrder createFromFinishedGoods(FinishedGoodsInspection inspection, LoginUser loginUser);

    /** 更新异常单（白名单 DTO 入参，禁止篡改系统字段） */
    void update(Long id, ExceptionUpdateDTO dto);

    /** 逻辑删除异常单 */
    void delete(Long id);

    /**
     * 发起整改流程：质量部门手动选择 CAPA / 8D / BOTH（系统不预填推荐值），
     * 同时完成 D0 发起（立案说明 + 指派 8D 团队 / CAPA 负责人），将 capa_status 由「待发起」推进为「进行中」。
     * 仅当 capa_status='待发起' 时可发起；已发起或已闭环的异常单不允许重复发起。
     */
    ExceptionOrder initiate(Long id, ExceptionInitiateDTO dto);

    /**
     * 人员选项列表：用于「发起整改」时选择责任人、组建 8D 团队、指派 CAPA 负责人。
     * 仅暴露 id/realName/roleCode/plantCode，供前端下拉/多选。
     * 区别于 /api/v1/admin/users（需 systemAdmin 权限），本接口走 M2 异常模块权限，相关部门（R03/R04/R06）可用。
     */
    List<ExceptionUserOptionVO> listUserOptions();

    /**
     * CAPA 根因审批（BOTH 模式专用）。
     * 8D 完成 D4 后需要 CAPA 质量部门审批根因分析结果，审批通过后 8D 方可推进至 D5。
     */
    void approveCapaRootCause(Long id, String comment);

    /**
     * CAPA 措施审批（BOTH 模式专用）。
     * 8D 完成 D5 措施制定后需要 CAPA 审批措施方案，审批通过后 8D 方可推进至 D6。
     */
    void approveCapaMeasures(Long id, String comment);

    /** 异常闭环（增强前置条件：全部整改计划/改善措施完成 + 最新验证通过 + 8D D8完成（若含8D）） */
    void close(Long id, ExceptionCloseDTO dto);

    /**
     * 闭环前置条件检查（逐项返回 PASS/FAIL/NA），供前端实时展示。
     */
    CloseReadinessVO closeReadiness(Long id);

    /**
     * BOTH 模式 CAPA 相位审批就绪检查。
     * 返回当前相位、当前用户是否有审批权限及详细说明，供前端实时展示审批面板。
     */
    CapaPhaseApprovalReadinessVO capaPhaseApprovalReadiness(Long id);

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


