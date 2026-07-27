package com.kangli.qms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kangli.qms.dto.EscalationCheckDTO;
import com.kangli.qms.dto.EscalationReviewDTO;
import com.kangli.qms.dto.EscalationPlanDTO;
import com.kangli.qms.dto.EscalationExecutionDTO;
import com.kangli.qms.dto.EscalationVerificationDTO;
import com.kangli.qms.dto.EscalationCloseDTO;
import com.kangli.qms.entity.Escalation;
import com.kangli.qms.vo.EscalationCheckResultVO;

public interface EscalationService extends IService<Escalation> {

    /** 批量升级检查（90天内同类不良≥N次） */
    EscalationCheckResultVO checkEscalation(EscalationCheckDTO dto);

    /** 审核自动触发的供应商升级任务 */
    Escalation review(Long id, EscalationReviewDTO dto);
    Escalation savePlan(Long id, EscalationPlanDTO dto);
    Escalation submitExecution(Long id, EscalationExecutionDTO dto);
    Escalation verify(Long id, EscalationVerificationDTO dto);
    Escalation close(Long id, EscalationCloseDTO dto);
}
