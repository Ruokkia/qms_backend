package com.kangli.qms.service.production.impl;

import com.kangli.qms.service.production.dto.DefectAnalyticsQuery;
import com.kangli.qms.domain.production.mapper.ProductionDefectAnalyticsMapper;
import com.kangli.qms.service.production.ProductionDefectAnalyticsService;
import com.kangli.qms.domain.production.vo.DefectAnalyticsSummaryVO;
import com.kangli.qms.domain.production.vo.DefectRankItemVO;
import com.kangli.qms.domain.production.vo.DefectTrendPointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ProductionDefectAnalyticsServiceImpl implements ProductionDefectAnalyticsService {

    private final ProductionDefectAnalyticsMapper mapper;

    private static BigDecimal pct(BigDecimal cur, BigDecimal base) {
        if (cur == null || base == null || base.compareTo(BigDecimal.ZERO) == 0) return null;
        return cur.subtract(base).multiply(BigDecimal.valueOf(100))
                .divide(base, 2, BigDecimal.ROUND_HALF_UP);
    }

    private static BigDecimal sum(List<DefectTrendPointVO> list, Function<DefectTrendPointVO, BigDecimal> f) {
        BigDecimal s = BigDecimal.ZERO;
        for (DefectTrendPointVO p : list) {
            BigDecimal v = f.apply(p);
            if (v != null) s = s.add(v);
        }
        return s;
    }

    private static long sumLong(List<DefectTrendPointVO> list, Function<DefectTrendPointVO, Long> f) {
        long s = 0;
        for (DefectTrendPointVO p : list) {
            Long v = f.apply(p);
            if (v != null) s += v;
        }
        return s;
    }

    @Override
    public DefectAnalyticsSummaryVO summary(DefectAnalyticsQuery q, String plantCode) {
        List<DefectTrendPointVO> cur = mapper.selectSeries(plantCode, q.getStart(), q.getEnd(), q.getMetric(), q.getGranularity(), bool(q.getExcludeDraft()));
        Window w = windows(q);
        List<DefectTrendPointVO> mom = mapper.selectSeries(plantCode, w.momStart, w.momEnd, q.getMetric(), q.getGranularity(), bool(q.getExcludeDraft()));
        List<DefectTrendPointVO> yoy = mapper.selectSeries(plantCode, w.yoyStart, w.yoyEnd, q.getMetric(), q.getGranularity(), bool(q.getExcludeDraft()));

        BigDecimal curVal = sum(cur, DefectTrendPointVO::getMetricValue);
        BigDecimal momVal = sum(mom, DefectTrendPointVO::getMetricValue);
        BigDecimal yoyVal = sum(yoy, DefectTrendPointVO::getMetricValue);
        long scrap = sumLong(cur, DefectTrendPointVO::getScrapQty);
        long repair = sumLong(cur, DefectTrendPointVO::getRepairCount);
        BigDecimal scrapRate = repair == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(scrap).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(repair), 2, BigDecimal.ROUND_HALF_UP);

        List<DefectRankItemVO> rank = mapper.selectRank(plantCode, q.getStart(), q.getEnd(), q.getMetric(), "process", bool(q.getExcludeDraft()), 1);
        DefectAnalyticsSummaryVO vo = new DefectAnalyticsSummaryVO();
        vo.setCurrentMetricValue(curVal);
        vo.setMomPct(pct(curVal, momVal));
        vo.setYoyPct(pct(curVal, yoyVal));
        vo.setScrapQty(scrap);
        vo.setScrapRate(scrapRate);
        vo.setTopProcess(rank.isEmpty() ? null : rank.get(0).getName());
        return vo;
    }

    @Override
    public List<DefectTrendPointVO> trend(DefectAnalyticsQuery q, String plantCode) {
        List<DefectTrendPointVO> cur = mapper.selectSeries(plantCode, q.getStart(), q.getEnd(), q.getMetric(), q.getGranularity(), bool(q.getExcludeDraft()));
        Window w = windows(q);
        List<DefectTrendPointVO> mom = mapper.selectSeries(plantCode, w.momStart, w.momEnd, q.getMetric(), q.getGranularity(), bool(q.getExcludeDraft()));
        List<DefectTrendPointVO> yoy = mapper.selectSeries(plantCode, w.yoyStart, w.yoyEnd, q.getMetric(), q.getGranularity(), bool(q.getExcludeDraft()));
        int n = Math.min(cur.size(), Math.min(mom.size(), yoy.size()));
        for (int i = 0; i < n; i++) {
            cur.get(i).setMomPct(pct(cur.get(i).getMetricValue(), mom.get(i).getMetricValue()));
            cur.get(i).setYoyPct(pct(cur.get(i).getMetricValue(), yoy.get(i).getMetricValue()));
        }
        return cur;
    }

    @Override
    public List<DefectRankItemVO> rank(DefectAnalyticsQuery q, String plantCode) {
        return mapper.selectRank(plantCode, q.getStart(), q.getEnd(), q.getMetric(), q.getDim(), bool(q.getExcludeDraft()), q.getTopN());
    }

    private Window windows(DefectAnalyticsQuery q) {
        LocalDate s = LocalDate.parse(q.getStart());
        LocalDate e = LocalDate.parse(q.getEnd());
        long days = ChronoUnit.DAYS.between(s, e) + 1;
        LocalDate momStart, momEnd;
        if ("custom".equals(q.getMomMode()) && q.getMomStart() != null) {
            momStart = LocalDate.parse(q.getMomStart());
            momEnd = momStart.plusDays(days - 1);
        } else {
            momStart = s.minusDays(days);
            momEnd = s.minusDays(1);
        }
        int yoy = q.getYoyYearsAgo() == null ? 1 : q.getYoyYearsAgo();
        LocalDate yoyStart = s.minusYears(yoy);
        LocalDate yoyEnd = e.minusYears(yoy);
        return new Window(momStart.toString(), momEnd.toString(), yoyStart.toString(), yoyEnd.toString());
    }

    private static boolean bool(Boolean b) { return b == null || b; }

    private static class Window {
        final String momStart, momEnd, yoyStart, yoyEnd;
        Window(String a, String b, String c, String d) { momStart = a; momEnd = b; yoyStart = c; yoyEnd = d; }
    }
}
