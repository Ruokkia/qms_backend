package com.kangli.qms.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class EscalationExecutionDTO {
    @NotBlank(message = "请填写执行跟踪记录")
    private String executionRecord;
}
