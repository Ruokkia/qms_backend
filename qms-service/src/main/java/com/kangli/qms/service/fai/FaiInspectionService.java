package com.kangli.qms.service.fai;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.service.fai.dto.FaiInspectionQuery;
import com.kangli.qms.service.fai.dto.FaiInspectionRecordResponse;
import com.kangli.qms.service.fai.dto.FaiItemValueRequest;
import com.kangli.qms.service.fai.dto.FaiReportResponse;
import com.kangli.qms.service.fai.dto.FaiSignatureRequest;
import com.kangli.qms.service.fai.dto.FaiSpcBaselineVO;
import com.kangli.qms.domain.spc.entity.SpcParameter;

import java.util.List;

/**
 * M3 首件检验 Service（核心业务：建单 / 录入判定 / 电子签名 / SPC 联动）。
 */
public interface FaiInspectionService {

    /**
     * 从变更触发创建首件检验单（复制标准模板参数项）。
     */
    FaiInspectionRecordResponse create(Long changeTriggerId, LoginUser loginUser);

    /**
     * 分页查询首件检验记录（支持 faiNo/batchNo/materialName/inspectionResult）。
     */
    PageResult<FaiInspectionRecordResponse> page(FaiInspectionQuery query, String plantCode);

    /**
     * 首件检验详情（含参数明细 + 签名列表）。
     */
    FaiInspectionRecordResponse detail(Long id);

    /**
     * 批量提交参数实际值并自动判定。
     */
    FaiInspectionRecordResponse submitItems(FaiItemValueRequest request, LoginUser loginUser);

    /**
     * 重新自动判定。
     */
    FaiInspectionRecordResponse judge(Long id, LoginUser loginUser);

    /**
     * 电子签名（写 SHA-256 摘要 + 触发 SPC 联动）。
     */
    FaiInspectionRecordResponse signature(FaiSignatureRequest request, LoginUser loginUser);

    /**
     * 报告视图（JSON）。
     */
    FaiReportResponse report(Long id);

    /**
     * 校验首件检验记录电子签名的完整性。
     * <p>未签名返回 {@link SignatureIntegrity#INTACT}；已签且内容绑定哈希一致返回 INTACT；
     * 已签但失配返回 TAMPERED（疑似篡改）；历史签名 content_hash 为空返回 LEGACY_UNVERIFIABLE。</p>
     */
    SignatureIntegrity verifySignatureIntegrity(Long id);

    /**
     * SPC 调取基准数据（按 param_code 分组的实际值）。
     */
    List<FaiSpcBaselineVO> spcBaseline(Long faiRecordId);

    /**
     * 刷新检验明细标准值：从当前激活标准同步最新值，并补全新标准项。
     */
    FaiInspectionRecordResponse refreshStandard(Long id, LoginUser loginUser);

    /** SPC 参数更新后，主动同步所有未签名首件记录的标准快照。 */
    void syncUpdatedSpcParameter(SpcParameter parameter, LoginUser loginUser);

    /** 手动重试将已签首件记录同步到 SPC 子组（用于签名时自动同步失败后的补救）。 */
    FaiInspectionRecordResponse resyncToSpc(Long id, LoginUser loginUser);
}
