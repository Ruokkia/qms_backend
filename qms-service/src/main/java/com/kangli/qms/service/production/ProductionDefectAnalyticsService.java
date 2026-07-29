package com.kangli.qms.service.production;

import com.kangli.qms.service.production.dto.DefectAnalyticsQuery;
import com.kangli.qms.domain.production.vo.DefectAnalyticsSummaryVO;
import com.kangli.qms.domain.production.vo.DefectRankItemVO;
import com.kangli.qms.domain.production.vo.DefectTrendPointVO;

import java.util.List;

public interface ProductionDefectAnalyticsService {
    DefectAnalyticsSummaryVO summary(DefectAnalyticsQuery q, String plantCode);
    List<DefectTrendPointVO> trend(DefectAnalyticsQuery q, String plantCode);
    List<DefectRankItemVO> rank(DefectAnalyticsQuery q, String plantCode);
}
