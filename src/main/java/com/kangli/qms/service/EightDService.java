package com.kangli.qms.service;

import com.kangli.qms.dto.EightDSaveDTO;
import com.kangli.qms.entity.Exception8d;
import com.kangli.qms.vo.EightDVO;

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
}
