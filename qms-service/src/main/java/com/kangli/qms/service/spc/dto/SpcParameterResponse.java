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
    /** 该参数下已录入的子组数量（用于前端删除守卫：>0 则不可删） */
    private Long subgroupCount;
    /** 该参数被 FAI 检验标准引用的次数（用于前端删除守卫：>0 则不可删） */
    private Long faiReferenceCount;
}
