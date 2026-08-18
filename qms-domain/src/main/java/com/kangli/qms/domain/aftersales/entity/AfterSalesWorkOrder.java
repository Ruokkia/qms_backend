package com.kangli.qms.domain.aftersales.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "after_sales_work_order", schema = "qms")
public class AfterSalesWorkOrder {
    @TableId(type = IdType.AUTO) private Long id;
    private String orderNo, customerName, customerContact, customerPhone, serviceType, productCode, productName, productBatchNo, faultDescription, serviceAddress, assigneeName, status, visitRecord, closeSignature, plantCode, plantName, createdBy, updatedBy;
    private LocalDateTime closeTime;
    @TableLogic private Short isDeleted;
    @Version private Integer version;
    private LocalDateTime createdAt, updatedAt;
}
