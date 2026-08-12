package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * SPC 工序响应。
 */
@Data
public class SpcProcessResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String processCode;
    private String processName;
    private String description;
    private Integer sortOrder;
    private String plantCode;
    private String plantName;
    private String isActive;
    private String changeRemark;

    /** 该工序按 processCode+plantCode 关联到的 FAI 检验标准数（is_deleted=0）；>0 则工序不可删 */
    private Long linkedStandardCount;
}
