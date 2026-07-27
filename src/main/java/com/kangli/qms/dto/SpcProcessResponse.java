package com.kangli.qms.dto;

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
}
