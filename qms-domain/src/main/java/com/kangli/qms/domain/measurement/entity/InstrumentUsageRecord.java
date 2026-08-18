package com.kangli.qms.domain.measurement.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.*;
@Data @TableName(value="instrument_usage_record",schema="qms") public class InstrumentUsageRecord { @TableId(type=IdType.AUTO) private Long id; private Long instrumentId; private String workOrderNo,productBatchNo,processName,inspectionRecordNo,measurementItem,measuredValue,measurementUnit,operatorName,plantCode,plantName,createdBy; private LocalDateTime usedAt,createdAt; @TableLogic private Short isDeleted; }
