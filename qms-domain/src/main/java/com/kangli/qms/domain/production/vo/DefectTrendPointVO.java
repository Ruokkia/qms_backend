package com.kangli.qms.domain.production.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 不良分析趋势点（按粒度分组，每点带环比/同比 delta）。
 */
@Data
public class DefectTrendPointVO {
    /** 周期首日（YYYY-MM-DD） */
    private String period;
    /** 本期该周期指标值 */
    private BigDecimal metricValue;
    /** 维修条数 */
    private Long repairCount;
    /** 报废数量 */
    private Long scrapQty;
    /** 环比变化率（按索引对齐对比期同位置周期；对比期=0 时为 null） */
    private BigDecimal momPct;
    /** 同比变化率（对比期=0 时为 null） */
    private BigDecimal yoyPct;
}
