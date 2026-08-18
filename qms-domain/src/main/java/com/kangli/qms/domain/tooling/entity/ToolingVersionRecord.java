package com.kangli.qms.domain.tooling.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName(value = "tooling_version_record", schema = "qms")
public class ToolingVersionRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private Long toolingId;
    private String versionNo;
    private String changeType;
    private String changeDescription;
    private LocalDate effectiveDate;
    private String changedBy;
    private String plantCode;
    private String plantName;
    private LocalDateTime createdAt;
    @TableLogic private Short isDeleted;
}
