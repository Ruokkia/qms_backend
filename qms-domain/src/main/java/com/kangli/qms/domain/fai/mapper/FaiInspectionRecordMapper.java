package com.kangli.qms.domain.fai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * M3 首件检验主记录 Mapper。
 */
public interface FaiInspectionRecordMapper extends BaseMapper<FaiInspectionRecord> {

    /**
     * 查询未关联异常单的不合格首件检验记录（供异常自动触发调度器全厂扫描）。
     * 绕过分公司拦截器以扫描所有厂区。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select({
        "SELECT r.* FROM qms.fai_inspection_record r",
        "WHERE r.is_deleted = 0",
        "  AND r.inspection_result = '不合格'",
        "  AND r.created_at >= #{startAt}",
        "  AND r.created_at <= #{endAt}",
        "  AND NOT EXISTS (",
        "    SELECT 1 FROM qms.exception_order e",
        "    WHERE e.is_deleted = 0 AND e.source_type = #{sourceType} AND e.source_id = r.id",
        "  )",
        "ORDER BY r.created_at DESC"
    })
    List<FaiInspectionRecord> selectUnlinkedUnqualified(@Param("sourceType") String sourceType,
                                                         @Param("startAt") LocalDateTime startAt,
                                                         @Param("endAt") LocalDateTime endAt);

    /**
     * 查询已签且合格的首件记录，按 (item_type, item_code) 聚合，供 SPC 统一代码字典使用。
     * 仅取已签（signature_status='已签'）且合格（inspection_result='合格'）且未删除的记录。
     */
    @Select({
        "SELECT item_type AS itemType, item_code AS itemCode, MAX(item_name) AS itemName,",
        "       MAX(process_code) AS processCode",
        "FROM qms.fai_inspection_record",
        "WHERE is_deleted = 0",
        "  AND signature_status = '已签' AND inspection_result = '合格'",
        "  AND item_code IS NOT NULL AND item_code <> ''",
        "GROUP BY item_type, item_code"
    })
    List<com.kangli.qms.common.SpcItemDictDTO> selectSignedDict(@Param("plantCode") String plantCode);
}
