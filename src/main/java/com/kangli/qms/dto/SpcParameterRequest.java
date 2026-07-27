package com.kangli.qms.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SPC 关键参数创建/更新请求。
 */
@Data
public class SpcParameterRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联工序 id */
    private Long processId;
    /** 参数编码 */
    private String paramCode;
    /** 参数名称 */
    private String paramName;
    /** 参数类型：尺寸/温度/压力/扭矩/电压 */
    private String paramType;
    /** 单位 */
    private String unit;
    /** 规格上限 USL */
    private BigDecimal upperSpecLimit;
    /** 规格下限 LSL */
    private BigDecimal lowerSpecLimit;
    /** 目标值 */
    private BigDecimal targetValue;
    /** 子组大小 n（2~10） */
    private Integer subgroupSize;
    /** 控制图类型：Xbar-R / Xbar-s */
    private String chartType;
    /** 是否启用：是/否 */
    private String isActive;
    /** 乐观锁版本（更新时必填） */
    private Integer version;
}
