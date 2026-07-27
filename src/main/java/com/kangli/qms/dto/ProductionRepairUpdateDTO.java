package com.kangli.qms.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产维修记录 更新 DTO（字段可选，留空表示不修改）。
 */
@Data
public class ProductionRepairUpdateDTO {

    private String repairNo;
    private String formName;
    private String formNo;
    private String productNo;
    private String productName;
    private String specModel;
    private String workOrderNo;
    private String productBatchOrSn;
    private String process;

    @DecimalMin(value = "0.001", message = "不良数量必须大于 0")
    private BigDecimal defectQty;

    private String defectPhenomenon;
    private String defectCode;
    private LocalDate sendRepairDate;
    private LocalDate repairDate;
    private String repairStatus;
    private String repairJudgmentResult;
    private String repairRecord;
    private String sendRepairer;
    private String repairer;
    private String auditor;
    private String auditStatus;
    private LocalDate auditDate;
    private String remark;
}
