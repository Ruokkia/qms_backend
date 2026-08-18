package com.kangli.qms.domain.supplier.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "supplier_assessment", schema = "qms")
public class SupplierAssessment {
    @TableId(type = IdType.AUTO) private Long id;
    private Long supplierId;
    private String assessmentPeriod;
    private BigDecimal qualifiedRate;
    private BigDecimal defectRate;
    private BigDecimal rectificationRate;
    private BigDecimal deliveryRate;
    private BigDecimal complianceRate;
    private String spcAssessment;
    private String grade;
    private String shareSuggestion;
    private String auditFrequency;
    private String admissionSuggestion;
    private String remark;
    private String plantCode;
    private String plantName;
    private String createdBy;
    private String updatedBy;
    @TableLogic private Short isDeleted;
    @Version private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
