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
    /** 条码（追溯标识，手动录入时必填） */
    private String barcode;
    private String materialCode;
    private String materialName;
    private String itemType;
    private String itemCode;
    /** 关联工序 spc_process.id（由 paramId 反查填充，便于前端 FAI 联动按工序定位） */
    private Long processId;
    private String processCode;
    /** 产品/物料名称（MATERIAL 取物料名；PRODUCT 取首件 itemName，落库在 materialName 兼容列） */
    private String itemName;
    /** 工序名称（由 processId 关联 spc_process 取） */
    private String processName;
    /** 参数名称（由 paramId 关联 spc_parameter 取） */
    private String paramName;
    /** 参数编码快照 */
    private String paramCode;
    /** 标准单位快照 */
    private String unit;
    /** 子组生成时所引用 SpcParameter 的目标值快照（当时标准副本） */
    private BigDecimal targetValue;
    /** 子组生成时所引用 SpcParameter 的规格上限快照 */
    private BigDecimal upperSpecLimit;
    /** 子组生成时所引用 SpcParameter 的规格下限快照 */
    private BigDecimal lowerSpecLimit;
    /** 首件标准版本快照 */
    private Integer standardVersion;
    private String subgroupStatus;
    private String plantCode;
    private String plantName;
    private List<SpcSampleResponse> samples;
}
