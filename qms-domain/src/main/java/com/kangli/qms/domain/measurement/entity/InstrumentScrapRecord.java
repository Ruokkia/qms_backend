package com.kangli.qms.domain.measurement.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.*;
@Data @TableName(value="instrument_scrap_record",schema="qms") public class InstrumentScrapRecord { @TableId(type=IdType.AUTO) private Long id; private Long instrumentId; private String scrapReason,currentCondition,disposalMethod,applicantName,metrologyApprover,qualityApprover,status,plantCode,plantName,createdBy,updatedBy; private LocalDateTime approvedAt,createdAt,updatedAt; @TableLogic private Short isDeleted; @Version private Integer version; }
