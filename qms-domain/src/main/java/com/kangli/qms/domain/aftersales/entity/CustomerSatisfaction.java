package com.kangli.qms.domain.aftersales.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "customer_satisfaction", schema = "qms")
public class CustomerSatisfaction {
    @TableId(type = IdType.AUTO) private Long id;
    private Long workOrderId;
    private Integer score;
    private String reasonDimension, followUpResult, responsibleName, capaNo, plantCode, plantName, createdBy, updatedBy;
    private LocalDateTime followUpTime;
    @TableLogic private Short isDeleted;
    @Version private Integer version;
    private LocalDateTime createdAt, updatedAt;
}
