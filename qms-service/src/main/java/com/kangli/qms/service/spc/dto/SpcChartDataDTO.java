package com.kangli.qms.service.spc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * SPC 控制图数据响应 DTO。包含控制限、子组数据点、过程能力指数和规格限。
 * <p>规格限（upperSpecLimit/lowerSpecLimit/targetValue）和 subgroupSize 在
 * 选定产品/物料后从 FAI 检验标准层解析，未选时为 null。</p>
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

    /** 当前过滤条件下的可用批次列表（用于前端批次筛选下拉） */
    private List<String> availableBatches;

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

    // ─── 过程能力指数（计算自所有已完成子组） ───
    private Double cp;       // 潜在过程能力
    private Double cpk;      // 实际过程能力（考虑偏倚）
    private Double pp;       // 整体潜在能力
    private Double ppk;      // 整体实际能力
    private Double sigmaWithin;   // 子组内标准差
    private Double sigmaOverall;  // 整体标准差

    // ─── 规格限（从 FAI 检验标准层解析，非参数字典回填；未选产品/物料时为 null） ───
    private BigDecimal upperSpecLimit;   // 规格上限 USL
    private BigDecimal lowerSpecLimit;   // 规格下限 LSL
    private BigDecimal targetValue;      // 目标值
    private Integer subgroupSize;        // 子组大小 n
}
