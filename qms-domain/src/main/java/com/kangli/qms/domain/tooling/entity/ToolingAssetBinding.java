package com.kangli.qms.domain.tooling.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "tooling_asset_binding", schema = "qms")
public class ToolingAssetBinding {
    @TableId(type = IdType.AUTO) private Long id;
    private Long toolingId;
    private String bindingType;
    private String productCode;
    private String productName;
    private String bomCode;
    private String processCode;
    private String processName;
    private String plantCode;
    private String plantName;
    private String createdBy;
    private LocalDateTime createdAt;
    @TableLogic private Short isDeleted;
}
