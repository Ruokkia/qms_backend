package com.kangli.qms.domain.tooling.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName(value = "tooling_asset", schema = "qms")
public class ToolingAsset {
    @TableId(type = IdType.AUTO) private Long id;
    private String toolingCode;
    private String qrCode;
    private String toolingName;
    private String toolingType;
    private String specification;
    private String applicableProduct;
    private String applicableProcess;
    private String material;
    private String supplierName;
    private LocalDate purchaseDate;
    private BigDecimal cost;
    private String versionNo;
    private String status;
    private String storageLocation;
    private String keeperName;
    private Integer lifeLimit;
    private Integer usedCount;
    private String plantCode;
    private String plantName;
    private String createdBy;
    private String updatedBy;
    @TableLogic private Short isDeleted;
    @Version private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
