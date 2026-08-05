package com.kangli.qms.service.exception;

import com.kangli.qms.service.exception.dto.EightDSaveDTO;
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
     * 推进到下一步。
     */
    EightDVO nextStep(Long exceptionId);

    /**
     * 查询 8D 步骤留痕（按操作时间升序）。
     */
    List<EightDStepLogVO> getStepLogs(Long exceptionId);
}
