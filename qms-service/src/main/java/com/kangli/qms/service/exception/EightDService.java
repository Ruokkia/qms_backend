package com.kangli.qms.service.exception;

import com.kangli.qms.service.exception.dto.EightDSaveDTO;
import com.kangli.qms.service.exception.dto.EightDD1TeamDTO;
import com.kangli.qms.service.exception.dto.EightDD1ReviewDTO;
import com.kangli.qms.service.exception.dto.StageApprovalDTO;
import com.kangli.qms.domain.exception.vo.EightDVO;
import com.kangli.qms.domain.exception.vo.EightDStepLogVO;

import java.util.List;

/**
 * 8D 报告服务。
 */
public interface EightDService {

    /**
     * 查询 8D 报告。
     */
    EightDVO getByExceptionId(Long exceptionId);

    /**
     * 保存/更新 8D 报告。
     */
    EightDVO saveOrUpdate(Long exceptionId, EightDSaveDTO dto);

    /**
     * 推进到下一步（若目标阶段配置了审批，则进入 PENDING_APPROVAL 等待审批）。
     * D1→D2 不走此方法，需通过 submitD1Team + reviewD1Team 完成。
     */
    EightDVO nextStep(Long exceptionId);

    /**
     * 阶段审批通过（解除 PENDING_APPROVAL，进入下一步可编辑）。需对应审批角色。
     */
    EightDVO approveStage(Long exceptionId, StageApprovalDTO dto);

    /**
     * 阶段审批驳回（回退到上一阶段重新填写，构成唯一允许的回退例外）。需对应审批角色。
     */
    EightDVO rejectStage(Long exceptionId, StageApprovalDTO dto);

    /**
     * D1 团队提交：负责人自行组建团队后提交质量部审核。
     */
    EightDVO submitD1Team(Long exceptionId, EightDD1TeamDTO dto);

    /**
     * D1 团队审核：质量部门审核负责人提交的团队构成。
     * 通过则 team 生效 currentStep 推进至 D2，驳回则退回 D1 由负责人重新组建。
     */
    EightDVO reviewD1Team(Long exceptionId, EightDD1ReviewDTO dto);

    /**
     * 查询 8D 步骤留痕（按操作时间升序）。
     */
    List<EightDStepLogVO> getStepLogs(Long exceptionId);
}
