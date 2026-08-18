package com.kangli.qms.domain.finishedgoods.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FinishedGoodsInspectionMapper extends BaseMapper<FinishedGoodsInspection> {

    /**
     * 查询已签且合格的首件记录，按 (item_type, item_code) 聚合，供 SPC 统一代码字典使用。
     * 仅取已签（signature_status='已签'）且合格（inspection_result='合格'）且未删除的记录。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select({
        "SELECT item_type AS itemType, item_code AS itemCode, MAX(item_name) AS itemName,",
        "       MAX(process_code) AS processCode",
        "FROM qms.finished_goods_inspection",
        "WHERE is_deleted = 0",
        "  AND signature_status = '已签' AND inspection_result = '合格'",
        "  AND item_code IS NOT NULL AND item_code <> ''",
        "GROUP BY item_type, item_code"
    })
    List<com.kangli.qms.common.SpcItemDictDTO> selectSignedDict(@Param("plantCode") String plantCode);
}
