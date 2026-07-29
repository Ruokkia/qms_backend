package com.kangli.qms.domain.production.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 不良分析高频排名项（按维度聚合，按 metric 排序）。
 */
@Data
public class DefectRankItemVO {
    /** 维度名称（工序 / 不良代码 / 不良现象） */
    private String name;
    /** 指标值（按 metric 聚合） */
    private BigDecimal metricValue;
    /** 维修条数 */
    private Long repairCount;
    /** 报废数量 */
    private Long scrapQty;
    /** 占该维度合计比例 %（百分比数值，不含 % 符号） */
    private BigDecimal sharePct;
}
