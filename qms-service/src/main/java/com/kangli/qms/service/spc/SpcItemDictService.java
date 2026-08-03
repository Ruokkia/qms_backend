package com.kangli.qms.service.spc;

import com.kangli.qms.common.SpcItemDictDTO;

import java.util.List;

/**
 * M4 SPC 统一代码字典 Service。
 * <p>权威源 = 已签首件（FAI 记录，signature_status='已签' 且 inspection_result='合格'）
 * ∪ 已激活标准（FAI 标准，is_active='是'），按 (itemType, itemCode) 去重。</p>
 */
public interface SpcItemDictService {

    /**
     * 查询统一代码字典。
     *
     * @param plantCode 厂区编码（隔离条件）
     * @param keyword   模糊关键字（匹配 itemCode / itemName，可空表示全部）
     * @return 去重后的字典项列表
     */
    List<SpcItemDictDTO> search(String plantCode, String keyword);
}
