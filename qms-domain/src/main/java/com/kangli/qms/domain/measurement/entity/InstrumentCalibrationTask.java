package com.kangli.qms.domain.measurement.entity;
import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.*;
@Data @TableName(value="instrument_calibration_task",schema="qms") public class InstrumentCalibrationTask{@TableId(type=IdType.AUTO)private Long id;private Long instrumentId;private LocalDate planDate;private String agency,responsibleName,status,plantCode,plantName,createdBy,updatedBy;private LocalDateTime completedAt;@TableLogic private Short isDeleted;@Version private Integer version;private LocalDateTime createdAt,updatedAt;}
