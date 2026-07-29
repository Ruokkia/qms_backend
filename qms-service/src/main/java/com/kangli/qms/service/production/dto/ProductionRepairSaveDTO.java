package com.kangli.qms.service.production.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生产维修记录 新增 DTO（结构化采集，禁止直接暴露实体）。
 */
@Data
public class ProductionRepairSaveDTO {

    @NotBlank(message = "维修编号不能为空")
    private String repairNo;

    private String formName;
    private String formNo;
    private String productNo;
    private String productName;
    private String specModel;
    private String workOrderNo;
    private String productBatchOrSn;

    @NotBlank(message = "生产工序不能为空")
    private String process;

    @NotNull(message = "不良数量不能为空")
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
