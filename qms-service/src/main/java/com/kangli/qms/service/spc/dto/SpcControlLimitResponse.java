package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPC 控制限响应 DTO（红线 #2：禁止直接返回持久化实体）。
 * <p>仅暴露计算所得的 X̄/R/s 图控制限与中心线等业务字段，不含审计列。</p>
 */
@Data
public class SpcControlLimitResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long paramId;
    private String chartType;
    private BigDecimal xbarUcl;
    private BigDecimal xbarCl;
    private BigDecimal xbarLcl;
    private BigDecimal rUcl;
    private BigDecimal rCl;
    private BigDecimal rLcl;
    private BigDecimal sUcl;
    private BigDecimal sCl;
    private BigDecimal sLcl;
    private Integer subgroupCount;
    private LocalDateTime calcDate;
    private String plantCode;
    private String plantName;
}
