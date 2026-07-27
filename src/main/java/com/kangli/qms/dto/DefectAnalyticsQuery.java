package com.kangli.qms.dto;

import lombok.Data;

/**
 * 不良分析统一查询参数。所有字段均映射到 production_repair 实际列，不引入新字段。
 */
@Data
public class DefectAnalyticsQuery {
    /** 分公司（默认取登录用户分公司，用于隔离；Controller 会强制覆盖为登录分公司） */
    private String plantCode;
    /** 本期起始 YYYY-MM-DD */
    private String start;
    /** 本期结束 YYYY-MM-DD */
    private String end;
    /** 是否排除草稿/未维修状态，默认 true */
    private Boolean excludeDraft = true;
    /** 度量：defectQty(不良数量)/repairCount(维修条数)/scrapQty(报废数量)，默认 defectQty */
    private String metric = "defectQty";
    /** 趋势维度：process/defectCode/defectPhenomenon */
    private String dim = "process";
    /** 时间粒度：MONTH/WEEK/QUARTER，默认 MONTH */
    private String granularity = "MONTH";
    /** 环比模式：auto(上一周期)/custom(自定义对比期)，默认 auto */
    private String momMode = "auto";
    /** 自定义对比期起始 YYYY-MM-DD（momMode=custom 时生效） */
    private String momStart;
    /** 同比回溯年数，默认 1 */
    private Integer yoyYearsAgo = 1;
    /** 排名 TopN，默认 10 */
    private Integer topN = 10;
}
