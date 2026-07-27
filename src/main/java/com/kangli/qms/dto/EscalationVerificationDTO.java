package com.kangli.qms.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class EscalationVerificationDTO {
    @NotBlank(message = "请选择验证结论")
    private String result;
    @NotBlank(message = "请填写验证依据")
    private String evidence;
}
