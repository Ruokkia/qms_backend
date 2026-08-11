package com.kangli.qms.service.spc;

import com.kangli.qms.service.spc.dto.SpcCapabilityResultDTO;

/**
 * M4 SPC 过程能力指数 Service（CP/CPK/PP/PPK 计算 + 判定）。
 */
public interface SpcCapabilityService {

    /**
     * 重新计算能力指数（子组数 &lt; 20 抛出业务异常）。
     *
     * @param paramId   SPC 参数 ID
     * @param plantCode 厂区编码
     * @param itemType  产品类型（PRODUCT/MATERIAL），可选；非空时从 FAI 标准层解析规格限
     * @param itemCode  产品/物料编码，可选；非空时从 FAI 标准层解析规格限
     * @param batchNo   批次号（暂用于子组过滤，可选）
     */
    SpcCapabilityResultDTO recalcCapability(Long paramId, String plantCode,
                                            String itemType, String itemCode, String batchNo);

    /**
     * 获取最新能力指数（无记录时返回 null）。
     *
     * @param paramId   SPC 参数 ID
     * @param plantCode 厂区编码
     * @param itemType  产品类型，可选；用于在返回结果中附带 FAI 标准层规格限
     * @param itemCode  产品/物料编码，可选；用于在返回结果中附带 FAI 标准层规格限
     * @param batchNo   批次号（可选）
     */
    SpcCapabilityResultDTO getLatest(Long paramId, String plantCode,
                                     String itemType, String itemCode, String batchNo);

    /**
     * 清空该参数的能力指数记录（子组不足时调用）。
     *
     * @param paramId   SPC 参数 ID
     * @param plantCode 厂区编码
     * @param itemType  产品类型（PRODUCT/MATERIAL），可选；非空时精确按维度删除
     * @param itemCode  产品/物料编码，可选；非空时精确按维度删除
     */
    void clear(Long paramId, String plantCode, String itemType, String itemCode);
}
