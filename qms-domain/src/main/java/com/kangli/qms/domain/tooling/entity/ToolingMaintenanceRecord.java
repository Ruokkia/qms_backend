package com.kangli.qms.domain.tooling.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName(value = "tooling_maintenance_record", schema = "qms")
public class ToolingMaintenanceRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private Long toolingId;
    private String recordType;
    private String planCycle;
    private LocalDate dueDate;
    private LocalDate executedDate;
    private String faultDescription;
    private String repairMeasure;
    private String rootCause;
    private String acceptanceResult;
    private String verificationResult;
    private String status;
    private String responsibleName;
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
