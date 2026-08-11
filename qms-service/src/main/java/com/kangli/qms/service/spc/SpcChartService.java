package com.kangli.qms.service.spc;

import com.kangli.qms.service.spc.dto.SpcChartDataDTO;
import com.kangli.qms.domain.spc.entity.SpcControlLimit;

/**
 * M4 SPC 控制图 Service（控制限计算 + 图数据组装）。
 */
public interface SpcChartService {

    /**
     * 重新计算并保存控制限（子组数 < 2 时清空）。
     * itemType/itemCode 非空时按该维度隔离子组并写入带维度的控制限；为空则按全局基线。
     */
    SpcControlLimit recalcControlLimits(Long paramId, String plantCode, String itemType, String itemCode);

    /** 获取控制图数据（含控制限与子组点）；itemType/itemCode/batchNo 为空时不过滤 */
    SpcChartDataDTO getChartData(Long paramId, String chartType, String plantCode, String itemType, String itemCode, String batchNo);
}
