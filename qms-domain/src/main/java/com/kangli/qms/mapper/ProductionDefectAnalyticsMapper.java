package com.kangli.qms.mapper;

import com.kangli.qms.vo.DefectRankItemVO;
import com.kangli.qms.vo.DefectTrendPointVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductionDefectAnalyticsMapper {

    /** 某一时间窗口内按粒度分组的序列（当前/环比/同比窗口各调用一次） */
    List<DefectTrendPointVO> selectSeries(
            @Param("plantCode") String plantCode,
            @Param("start") String start,
            @Param("end") String end,
            @Param("metric") String metric,
            @Param("granularity") String granularity,
            @Param("excludeDraft") boolean excludeDraft);

    /** 高频排名（按 dim 维度、metric 度量） */
    List<DefectRankItemVO> selectRank(
            @Param("plantCode") String plantCode,
            @Param("start") String start,
            @Param("end") String end,
            @Param("metric") String metric,
            @Param("dim") String dim,
            @Param("excludeDraft") boolean excludeDraft,
            @Param("topN") int topN);
}
