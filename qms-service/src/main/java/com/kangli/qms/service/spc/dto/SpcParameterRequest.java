package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;

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
    /** 是否启用：是/否 */
    private String isActive;
    /** 小数位数 */
    private Integer decimalPlaces;
    /** 是否关键特性：是/否 */
    private String isCritical;
    /** 变更备注 */
    private String changeRemark;
    /** 乐观锁版本（更新时必填） */
    private Integer version;
}
