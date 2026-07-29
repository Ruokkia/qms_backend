package com.kangli.qms.service.production.impl;

import com.kangli.qms.service.production.dto.DefectAnalyticsQuery;
import com.kangli.qms.service.production.ProductionDefectAnalyticsService;
import com.kangli.qms.domain.production.vo.DefectAnalyticsSummaryVO;
import com.kangli.qms.domain.production.vo.DefectRankItemVO;
import com.kangli.qms.domain.production.vo.DefectTrendPointVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 不良分析 Service 单测。仅基于 production_repair 实际列聚合，不引入新字段。
 * 测试库仅含 2026 年数据，因此 auto 模式环比/同比对比期无数据 → 返回 null（空对比期设计预期）。
 */
@SpringBootTest
class ProductionDefectAnalyticsServiceTest {

    @Autowired
    private ProductionDefectAnalyticsService service;

    private DefectAnalyticsQuery base() {
        DefectAnalyticsQuery q = new DefectAnalyticsQuery();
        q.setStart("2026-01-01");
        q.setEnd("2026-12-31");
        q.setMetric("defectQty");
        q.setDim("process");
        q.setGranularity("MONTH");
        q.setExcludeDraft(true);
        q.setMomMode("auto");
        q.setYoyYearsAgo(1);
        q.setTopN(10);
        return q;
    }

    @Test
    void summary_sz_2026_auto_noHistory_compareNull() {
        DefectAnalyticsSummaryVO vo = service.summary(base(), "SZ");
        assertNotNull(vo.getCurrentMetricValue());
        assertTrue(vo.getCurrentMetricValue().compareTo(BigDecimal.ZERO) >= 0);
        // 库内无 2025 历史，对比期为空 → null（验证空对比期健壮性、无除零）
        assertNull(vo.getMomPct());
        assertNull(vo.getYoyPct());
        assertNotNull(vo.getScrapQty());
        assertTrue(vo.getScrapQty() >= 0);
        assertNotNull(vo.getScrapRate());
        assertTrue(vo.getScrapRate().compareTo(BigDecimal.ZERO) >= 0);
        assertNotNull(vo.getTopProcess());
    }

    @Test
    void trend_sz_2026_pointsValid() {
        List<DefectTrendPointVO> points = service.trend(base(), "SZ");
        assertFalse(points.isEmpty());
        for (DefectTrendPointVO p : points) {
            assertNotNull(p.getPeriod());
            assertNotNull(p.getMetricValue());
            assertNotNull(p.getRepairCount());
            assertNotNull(p.getScrapQty());
        }
    }

    @Test
    void rank_sz_2026_descAndShare() {
        List<DefectRankItemVO> items = service.rank(base(), "SZ");
        assertFalse(items.isEmpty());
        for (int i = 0; i < items.size(); i++) {
            DefectRankItemVO it = items.get(i);
            assertNotNull(it.getName());
            assertNotNull(it.getMetricValue());
            assertNotNull(it.getSharePct());
            if (i > 0) {
                assertTrue(items.get(i - 1).getMetricValue().compareTo(it.getMetricValue()) >= 0,
                        "rank should be descending by metricValue");
            }
        }
    }

    @Test
    void summary_customMom_noData_compareNull() {
        DefectAnalyticsQuery q = base();
        q.setMomMode("custom");
        q.setMomStart("2025-01-01"); // 库内无数据
        DefectAnalyticsSummaryVO vo = service.summary(q, "SZ");
        assertNull(vo.getMomPct());
        assertNotNull(vo.getCurrentMetricValue());
    }

    @Test
    void summary_metricRepairCount_returnsCount() {
        DefectAnalyticsQuery q = base();
        q.setMetric("repairCount");
        DefectAnalyticsSummaryVO vo = service.summary(q, "SZ");
        assertNotNull(vo.getCurrentMetricValue());
        // repairCount 应为整数（条数）
        assertTrue(vo.getCurrentMetricValue().stripTrailingZeros().scale() <= 0
                || vo.getCurrentMetricValue().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0);
    }
}
