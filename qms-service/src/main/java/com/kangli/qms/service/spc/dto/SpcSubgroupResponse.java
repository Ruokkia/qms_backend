package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SPC 子组响应（含样本明细列表）。
 */
@Data
public class SpcSubgroupResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long paramId;
    private String subgroupNo;
    private Integer sampleCount;
    private BigDecimal meanValue;
    private BigDecimal rangeValue;
    private BigDecimal stdDev;
    private LocalDateTime sampleTime;
    private String sourceType;
    private Long faiRecordId;
    private String workOrderNo;
    private String batchNo;
    private String materialCode;
    private String materialName;
    private String itemType;
    private String itemCode;
    /** 关联工序 spc_process.id（由 paramId 反查填充，便于前端 FAI 联动按工序定位） */
    private Long processId;
    private String processCode;
    private String subgroupStatus;
    private String plantCode;
    private String plantName;
    private List<SpcSampleResponse> samples;
}
