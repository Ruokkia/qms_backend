package com.kangli.qms.service.exception.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 供应商升级审核请求。
 */
@Data
public class EscalationReviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** APPROVE / REJECT */
    @NotBlank(message = "审核决定不能为空")
    private String decision;

    @NotBlank(message = "审核意见不能为空")
    private String opinion;
}
