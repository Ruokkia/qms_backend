package com.kangli.qms.domain.aftersales.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "after_sales_work_order_log", schema = "qms")
public class AfterSalesWorkOrderLog {
    @TableId(type = IdType.AUTO) private Long id;
    private Long workOrderId;
    private String beforeStatus, afterStatus, operatorName, remark, plantCode;
    private LocalDateTime createdAt;
}
