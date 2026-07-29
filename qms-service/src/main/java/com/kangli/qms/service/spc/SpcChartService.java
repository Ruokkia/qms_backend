package com.kangli.qms.service.spc;

import com.kangli.qms.service.spc.dto.SpcChartDataDTO;
import com.kangli.qms.domain.spc.entity.SpcControlLimit;

/**
 * M4 SPC 控制图 Service（控制限计算 + 图数据组装）。
 */
public interface SpcChartService {

    /** 重新计算并保存控制限（子组数 < 2 时清空） */
    SpcControlLimit recalcControlLimits(Long paramId, String plantCode);

    /** 获取控制图数据（含控制限与子组点） */
    SpcChartDataDTO getChartData(Long paramId, String chartType, String plantCode);
}
