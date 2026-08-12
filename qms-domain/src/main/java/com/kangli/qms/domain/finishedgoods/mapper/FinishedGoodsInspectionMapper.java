package com.kangli.qms.domain.finishedgoods.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface FinishedGoodsInspectionMapper extends BaseMapper<FinishedGoodsInspection> {

    /**
     * 查询未关联异常单的不合格成品检验记录（供异常自动触发调度器全厂扫描）。
     * 条件：检验结论为不合格且不合格数量大于 0。绕过分公司拦截器以扫描所有厂区。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select({
        "SELECT f.* FROM qms.finished_goods_inspection f",
        "WHERE f.is_deleted = 0",
        "  AND f.inspection_result = '不合格'",
        "  AND f.unqualified_qty > 0",
        "  AND f.created_at >= #{startAt}",
        "  AND f.created_at <= #{endAt}",
        "  AND NOT EXISTS (",
        "    SELECT 1 FROM qms.exception_order e",
        "    WHERE e.is_deleted = 0 AND e.source_type = #{sourceType} AND e.source_id = f.id",
        "  )",
        "ORDER BY f.created_at DESC"
    })
    List<FinishedGoodsInspection> selectUnlinkedUnqualified(@Param("sourceType") String sourceType,
                                                             @Param("startAt") LocalDateTime startAt,
                                                             @Param("endAt") LocalDateTime endAt);
}
