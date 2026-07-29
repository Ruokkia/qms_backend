package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SPC 过程能力指数响应。
 */
@Data
public class SpcCapabilityResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long paramId;
    private BigDecimal cp;
    private BigDecimal cpk;
    private BigDecimal pp;
    private BigDecimal ppk;
    private BigDecimal cpu;
    private BigDecimal cpl;
    private Integer sampleCount;
    private Integer subgroupCount;
    /** 判定：充足/需改进/不足 */
    private String judgment;
}
