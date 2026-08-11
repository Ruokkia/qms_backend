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

    /**
     * 按条件查询一条激活的检验标准（产品类型+编码+工序+厂区），按版本降序取最新。
     * <p>用于 SPC 控制图/能力分析从 FAI 标准层解析规格限。</p>
     *
     * @param itemType    产品类型（PRODUCT/MATERIAL）
     * @param itemCode    物料或产品编码
     * @param processCode 工序编码（如 ASM/WDG/INS）
     * @param plantCode   厂区编码
     * @return 激活的检验标准，无匹配时返回 null
     */
    @Select({
        "SELECT id, item_type, item_code, item_name,",
        "       process_code, process_name, std_version,",
        "       is_active, plant_code, plant_name, created_by, updated_by,",
        "       is_deleted, version, created_at, updated_at",
        "FROM qms.fai_inspection_standard",
        "WHERE is_deleted = 0",
        "  AND is_active = '是'",
        "  AND item_type = #{itemType}",
        "  AND item_code = #{itemCode}",
        "  AND process_code = #{processCode}",
        "  AND plant_code = #{plantCode}",
        "ORDER BY std_version DESC LIMIT 1"
    })
    FaiInspectionStandard selectActiveByCondition(
            @Param("itemType") String itemType,
            @Param("itemCode") String itemCode,
            @Param("processCode") String processCode,
            @Param("plantCode") String plantCode);
}
