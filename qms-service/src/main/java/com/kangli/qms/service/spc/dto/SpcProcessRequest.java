package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * SPC 工序创建/更新请求。
 */
@Data
public class SpcProcessRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工序编码（ASM/WDG/INS） */
    private String processCode;
    /** 工序名称（装配/焊接/检测） */
    private String processName;
    /** 工序描述 */
    private String description;
    /** 排序 */
    private Integer sortOrder;
    /** 是否启用：是/否 */
    private String isActive;

    /** 变更备注 */
    private String changeRemark;

    /** 乐观锁版本（更新时必填） */
    private Integer version;
}
