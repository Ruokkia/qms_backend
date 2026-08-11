package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;

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
    private java.math.BigDecimal upperSpecLimit;
    private java.math.BigDecimal lowerSpecLimit;
    private java.math.BigDecimal targetValue;
    private Integer subgroupSize;
    private String chartType;
    private String isActive;
    private Integer decimalPlaces;
    private String isCritical;
    private String changeRemark;
    private String plantCode;
    private String plantName;
}
