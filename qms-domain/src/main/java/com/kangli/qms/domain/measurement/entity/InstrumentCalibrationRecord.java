package com.kangli.qms.domain.measurement.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.*;
@Data @TableName(value="instrument_calibration_record",schema="qms") public class InstrumentCalibrationRecord { @TableId(type=IdType.AUTO) private Long id; private Long instrumentId; private String result,agency,certificateNo,responsibleName,remark,plantCode,plantName,createdBy,updatedBy; private LocalDate planDate,calibrationDate,validUntil; @TableLogic private Short isDeleted; @Version private Integer version; private LocalDateTime createdAt,updatedAt; }
