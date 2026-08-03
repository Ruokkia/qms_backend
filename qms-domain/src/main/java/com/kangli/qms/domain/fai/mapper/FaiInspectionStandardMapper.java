package com.kangli.qms.domain.fai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.fai.entity.FaiInspectionStandard;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * M3 首件检验标准模板 Mapper。
 */
public interface FaiInspectionStandardMapper extends BaseMapper<FaiInspectionStandard> {

    /**
     * 查询已激活（is_active='是'）的检验标准，按 (item_type, item_code) 聚合，供 SPC 统一代码字典使用。
     * FAI 标准无“已签”字段，以 is_active='是' 等价“已发布生效”，与已签首件并集成统一字典。
     */
    @Select({
        "SELECT item_type AS itemType, item_code AS itemCode, MAX(item_name) AS itemName,",
        "       MAX(process_code) AS processCode",
        "FROM qms.fai_inspection_standard",
        "WHERE is_deleted = 0",
        "  AND is_active = '是'",
        "  AND item_code IS NOT NULL AND item_code <> ''",
        "GROUP BY item_type, item_code"
    })
    List<com.kangli.qms.common.SpcItemDictDTO> selectActiveStandardDict(@Param("plantCode") String plantCode);
}
