package com.kangli.qms.domain.exception.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.production.vo.BreakdownItemVO;
import com.kangli.qms.domain.exception.vo.ExceptionAnalysisItemVO;
import com.kangli.qms.domain.exception.vo.ExceptionStatsVO;
import com.kangli.qms.domain.supplier.vo.SupplierExceptionSummaryVO;
import com.kangli.qms.domain.exception.vo.TriggeredSupplierVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 异常单 Mapper — 含统计分析 + 重复问题升级检查 + 供应商频次汇总的自定义查询。
 */
public interface ExceptionOrderMapper extends BaseMapper<ExceptionOrder> {

    /**
     * KPI 看板统计。
     */
    ExceptionStatsVO selectStats(@Param("plantCode") String plantCode);

    /**
     * 按严重等级分组统计。
     */
    List<BreakdownItemVO> selectSeverityBreakdown(@Param("plantCode") String plantCode);

    /**
     * 按来源类型分组统计。
     */
    List<BreakdownItemVO> selectSourceBreakdown(@Param("plantCode") String plantCode);

    /**
     * 多维度分析（按 defectDesc / supplier / material / time 聚合）。
     */
    List<ExceptionAnalysisItemVO> selectAnalysis(@Param("plantCode") String plantCode,
                                                   @Param("dimension") String dimension);

    /**
     * 重复问题检查（找 90 天内同类不良≥N 次的供应商）。
     */
    List<TriggeredSupplierVO> selectRepeatExceptions(@Param("plantCode") String plantCode,
                                                      @Param("daysWindow") int daysWindow,
                                                      @Param("minRepeatCount") int minRepeatCount);

    /**
     * 供应商来料不良频次汇总。
     */
    List<SupplierExceptionSummaryVO> selectSupplierSummary(@Param("plantCode") String plantCode,
                                                            @Param("supplierId") Long supplierId,
                                                            @Param("startDate") String startDate,
                                                            @Param("endDate") String endDate,
                                                            @Param("minCount") Integer minCount);
}

