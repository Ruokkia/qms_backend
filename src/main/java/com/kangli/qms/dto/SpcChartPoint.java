package com.kangli.qms.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * SPC 控制图单点（一个子组）。
 */
@Data
public class SpcChartPoint implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 子组编号 */
    private String subgroupNo;
    /** 子组均值 X̄ */
    private BigDecimal x;
    /** 子组极差 R（Xbar-R） */
    private BigDecimal r;
    /** 子组标准差 s（Xbar-s） */
    private BigDecimal s;
    /** 样本明细（用于悬停展示） */
    private List<BigDecimal> samples;
}
