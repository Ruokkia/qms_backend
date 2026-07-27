package com.kangli.qms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * SPC 控制图数据响应。
 * <p>X̄ 折线 + 控制限；下方 R 或 s 折线 + 控制限（依 chartType 取用）。</p>
 */
@Data
public class SpcChartDataDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long paramId;
    private String paramCode;
    private String paramName;
    private String chartType;
    /** 子组点序列 */
    private List<SpcChartPoint> points;

    // X̄ 图控制限
    private BigDecimal xbarUcl;
    private BigDecimal xbarCl;
    private BigDecimal xbarLcl;
    // R 图控制限（Xbar-R 用）
    @JsonProperty("rUcl")
    private BigDecimal rUcl;
    @JsonProperty("rCl")
    private BigDecimal rCl;
    @JsonProperty("rLcl")
    private BigDecimal rLcl;
    // s 图控制限（Xbar-s 用）
    @JsonProperty("sUcl")
    private BigDecimal sUcl;
    @JsonProperty("sCl")
    private BigDecimal sCl;
    @JsonProperty("sLcl")
    private BigDecimal sLcl;
}
