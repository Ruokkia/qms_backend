package com.kangli.qms.domain.measurement.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.*;
@Data @TableName(value="calibration_agency",schema="qms") public class CalibrationAgency {
 @TableId(type=IdType.AUTO) private Long id; private String agencyName,certificateNo,contactName,contactPhone,status,remark,plantCode,plantName,createdBy,updatedBy;
 private LocalDate qualificationExpiryDate; @TableLogic private Short isDeleted; @Version private Integer version; private LocalDateTime createdAt,updatedAt;
}
