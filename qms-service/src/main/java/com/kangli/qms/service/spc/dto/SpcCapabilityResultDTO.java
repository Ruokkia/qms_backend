package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SPC 过程能力指数响应。
 * <p>规格限（upperSpecLimit/lowerSpecLimit/targetValue）和 subgroupSize 在
 * 选定产品/物料后从 FAI 检验标准层解析，未选时为 null。</p>
 */
@Data
public class SpcCapabilityResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long paramId;
    private BigDecimal cp;
    private BigDecimal cpk;
    private BigDecimal pp;
    private BigDecimal ppk;
    private BigDecimal cpu;
    private BigDecimal cpl;
    private Integer sampleCount;
    private Integer subgroupCount;
    /** 判定：充足/需改进/不足 */
    private String judgment;

    // ─── 规格限（从 FAI 检验标准层解析，非参数字典回填；未选产品/物料时为 null） ───
    private BigDecimal upperSpecLimit;   // 规格上限 USL
    private BigDecimal lowerSpecLimit;   // 规格下限 LSL
    private BigDecimal targetValue;      // 目标值
    private Integer subgroupSize;        // 子组大小 n
}
