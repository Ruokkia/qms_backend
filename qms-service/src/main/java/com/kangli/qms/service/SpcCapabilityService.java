package com.kangli.qms.service;

import com.kangli.qms.dto.SpcCapabilityResultDTO;

/**
 * M4 SPC 过程能力指数 Service（CP/CPK/PP/PPK 计算 + 判定）。
 */
public interface SpcCapabilityService {

    /** 重新计算能力指数（子组数 < 20 抛出业务异常） */
    SpcCapabilityResultDTO recalcCapability(Long paramId, String plantCode);

    /** 获取最新能力指数（无记录时返回 null） */
    SpcCapabilityResultDTO getLatest(Long paramId, String plantCode);

    /** 清空该参数的能力指数记录（子组不足时调用） */
    void clear(Long paramId, String plantCode);
}
