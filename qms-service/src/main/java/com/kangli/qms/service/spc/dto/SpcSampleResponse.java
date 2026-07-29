package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SPC 采样明细响应。
 */
@Data
public class SpcSampleResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long subgroupId;
    private Integer sampleNo;
    private BigDecimal sampleValue;
}
