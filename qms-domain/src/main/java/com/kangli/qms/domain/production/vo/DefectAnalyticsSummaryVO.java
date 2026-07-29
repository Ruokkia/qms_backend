package com.kangli.qms.domain.production.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 不良分析概览。
 */
@Data
public class DefectAnalyticsSummaryVO {
    /** 本期指标值（按 metric） */
    private BigDecimal currentMetricValue;
    /** 环比变化率（对比期=0 时为 null） */
    private BigDecimal momPct;
    /** 同比变化率（对比期=0 时为 null） */
    private BigDecimal yoyPct;
    /** 报废数量 */
    private Long scrapQty;
    /** 报废率 = 报废数量 / 维修条数（百分比数值，不含 % 符号） */
    private BigDecimal scrapRate;
    /** 高频工序 TOP1（按 metric 排序） */
    private String topProcess;
}
