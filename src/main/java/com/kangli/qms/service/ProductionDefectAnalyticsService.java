package com.kangli.qms.service;

import com.kangli.qms.dto.DefectAnalyticsQuery;
import com.kangli.qms.vo.DefectAnalyticsSummaryVO;
import com.kangli.qms.vo.DefectRankItemVO;
import com.kangli.qms.vo.DefectTrendPointVO;

import java.util.List;

public interface ProductionDefectAnalyticsService {
    DefectAnalyticsSummaryVO summary(DefectAnalyticsQuery q, String plantCode);
    List<DefectTrendPointVO> trend(DefectAnalyticsQuery q, String plantCode);
    List<DefectRankItemVO> rank(DefectAnalyticsQuery q, String plantCode);
}
