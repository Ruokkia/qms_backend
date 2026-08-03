package com.kangli.qms.service.spc.dto;

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
    /** 分类：PRODUCT(产品)/MATERIAL(物料) */
    private String itemType;
    /** 产品/物料代码（随 itemType 取值，控制图关联维度） */
    private String itemCode;
    /** 来源批次号（子组级，用于悬停展示） */
    private String batchNo;
    /** 子组均值 X̄ */
    private BigDecimal x;
    /** 子组极差 R（Xbar-R） */
    private BigDecimal r;
    /** 子组标准差 s（Xbar-s） */
    private BigDecimal s;
    /** 样本明细（含条码，用于悬停展示） */
    private List<SpcSampleResponse> samples;
}
