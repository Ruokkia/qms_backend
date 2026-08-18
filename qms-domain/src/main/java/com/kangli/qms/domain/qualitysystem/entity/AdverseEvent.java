package com.kangli.qms.domain.qualitysystem.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.*;
@Data @TableName(value="adverse_event",schema="qms") public class AdverseEvent {
 @TableId(type=IdType.AUTO) private Long id;
 private String productName,productModel,productBatchNo,eventDescription,harmLevel,patientInfoMasked,reporterName,reportSource,assessmentResult,assessorName,reportReceiptNo,rootCause,correctiveAction,responsibleName,capaNo,status,plantCode,plantName,createdBy,updatedBy;
 private LocalDateTime eventDate,createdAt,updatedAt; private LocalDate reportDeadline,reportDate;
 @TableLogic private Short isDeleted; @Version private Integer version;
}
