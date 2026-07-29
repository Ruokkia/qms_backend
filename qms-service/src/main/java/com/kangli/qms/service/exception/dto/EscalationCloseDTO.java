package com.kangli.qms.service.exception.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class EscalationCloseDTO {
    @NotBlank(message = "请填写关闭审批意见")
    private String reason;
}
