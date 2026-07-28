package com.kangli.qms.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SPC 关键参数响应。
 */
@Data
public class SpcParameterResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long processId;
    private String paramCode;
    private String paramName;
    private String paramType;
    private String unit;
    private BigDecimal upperSpecLimit;
    private BigDecimal lowerSpecLimit;
    private BigDecimal targetValue;
    private Integer subgroupSize;
    private String chartType;
    private String isActive;
    private String plantCode;
    private String plantName;
}
