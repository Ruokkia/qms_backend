package com.kangli.qms.service.fai;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.service.fai.dto.FaiStandardProcessVO;
import com.kangli.qms.service.fai.dto.FaiStandardResponse;
import com.kangli.qms.service.fai.dto.FaiStandardSaveRequest;
import com.kangli.qms.service.fai.dto.FaiStandardHistoryResponse;
import com.kangli.qms.service.fai.dto.FaiStandardApprovalResponse;
import com.kangli.qms.service.fai.dto.FaiStandardSpcParamVO;

import java.util.List;

/**
 * M3 首件检验标准模板 Service。
 */
public interface FaiStandardService {

    /**
     * 标准模板列表（按分公司隔离）。
     */
    List<FaiStandardResponse> list(String plantCode);

    /**
     * 标准模板列表（按分公司 + 分类隔离）。itemType 为空时返回全部。
     */
    List<FaiStandardResponse> listByItemType(String plantCode, String itemType);

    /**
     * 查询某 物料+工序 的最新激活标准（含参数项）。
     */
    FaiStandardResponse latestActive(String materialCode, String processName, String plantCode);

    /**
     * 查询某 分类+代码+工序 的最新激活标准（含参数项）。区分产品/物料模板。
     */
    FaiStandardResponse latestActive(String itemCode, String itemType, String processName, String plantCode);

    /**
     * 按 分类+代码+工序+指定版本号 定位标准（含参数项）。
     * stdVersion 非 null 时精确命中该版本；为 null 时回退取最新激活版本（兼容历史数据）。
     * 用于版本隔离：首件记录建单时固化的标准版本，其刷新标准/标准变更对比应锁定自身版本。
     */
    FaiStandardResponse latestActive(String itemCode, String itemType, String processName, Integer stdVersion, String plantCode);

    /**
     * 新增标准模板（含参数项）。返回新标准 id。
     */
    Long createStandard(FaiStandardSaveRequest req, LoginUser loginUser);

    /**
     * 更新标准模板（含参数项）。覆盖式更新参数项明细。
     */
    void updateStandard(Long id, FaiStandardSaveRequest req, LoginUser loginUser);

    /**
     * 逻辑删除标准模板（含参数项）。
     */
    void deleteStandard(Long id, LoginUser loginUser);

    /**
     * 按 id 查询标准模板（含参数项），不存在返回 null。
     * 受厂区隔离约束，仅能查询当前厂区数据。
     */
    FaiStandardResponse getStandard(Long id, String plantCode);

    /**
     * 查询标准的变更历史列表（按时间倒序）。P0：变更追溯。
     * 受厂区隔离约束，仅能查询当前厂区数据。
     */
    List<FaiStandardHistoryResponse> getHistory(Long standardId, String plantCode);

    // ========== P1：轻量级审批工作流 ==========

    /**
     * 提交变更请求至审批队列（不直接执行），返回审批记录 ID。
     */
    Long submitForApproval(FaiStandardSaveRequest req, LoginUser loginUser);

    /**
     * 查询当前厂区的待审批列表。
     */
    List<FaiStandardApprovalResponse> getPendingApprovals(LoginUser loginUser);

    /**
     * 审批通过 —— 执行实际变更并记录。
     */
    void approveStandard(Long approvalId, LoginUser loginUser);

    /**
     * 驳回审批。
     */
    void rejectStandard(Long approvalId, String rejectReason, LoginUser loginUser);

    // ========== P2：版本管理规范化 ==========

    /**
     * 创建新版本 —— 克隆现有标准生成新版本行（version+1），旧行标记为非激活。
     * 用于审批通过后执行。
     */
    Long createNewVersion(Long standardId, FaiStandardSaveRequest req, LoginUser loginUser);

    /**
     * 回滚到历史版本 —— 基于历史快照重建标准，生成新版本行。
     */
    Long rollbackToVersion(Long standardId, Long historyId, LoginUser loginUser);

    // ========== P3：定期复审提醒 ==========

    /**
     * 标记标准已复审（更新 lastReviewedAt）。
     */
    void markReviewed(Long standardId, LoginUser loginUser);

    /**
     * 查询复审逾期的标准列表（lastReviewedAt 距今超过 reviewIntervalDays）。
     */
    List<FaiStandardResponse> getOverdueReviews(String plantCode);

    /**
     * 按分类 + 代码 + 厂区从检验标准中去重取已维护工序列表（processCode/processName）。
     * 供变更触发工序下拉使用，保证下拉项与标准一致。
     * itemCode 非空时仅返回该代码绑定的工序；为空时返回该分类下全部已维护工序。
     */
    List<FaiStandardProcessVO> listProcessesByItemType(String plantCode, String itemType, String itemCode);

    /**
     * SPC 数据采集专用：查询某 分类+代码+工序 的最新激活标准中 spcEnabled="是" 的参数项，
     * 返回 FaiStandardSpcParamVO 列表（含 USL/LSL/目标值/子组大小/控制图类型）。
     * spcParameterId 同时用于 SPC 参数字典补全 subgroupSize/chartType 的降级。
     */
    List<FaiStandardSpcParamVO> listSpcParams(String itemType, String itemCode, String processName, String plantCode);
}
