package com.kangli.qms.service.exception.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import java.time.LocalDate;

@Data
public class EscalationPlanDTO {
    @NotBlank(message = "请填写升级措施与计划")
    private String actionPlan;
    @NotBlank(message = "请填写责任人")
    private String ownerName;
    /** 责任人 ID（点对点通知使用） */
    private Long ownerId;
    private LocalDate dueDate;
}
