package com.kangli.qms.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class AdminActionRequest {
    @NotBlank private String reason;
    private String password;
}
