package com.kangli.qms.service.fai.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 电子签名请求（M3）。
 * <p>后端仅存 SHA-256 摘要，不存明文密码。</p>
 */
@Data
@ApiModel(description = "电子签名请求")
public class FaiSignatureRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "首件检验记录ID不能为空")
    @ApiModelProperty(value = "首件检验记录ID", required = true)
    private Long faiRecordId;

    @NotBlank(message = "签名人ID不能为空")
    @ApiModelProperty(value = "签名人ID（登录用户）", required = true)
    private String signerId;

    @ApiModelProperty(value = "签名人姓名")
    private String signerName;

    @NotBlank(message = "签名类型不能为空")
    @ApiModelProperty(value = "签名类型：检验签/审核签", required = true)
    private String signType;

    @NotBlank(message = "签名原因不能为空")
    @ApiModelProperty(value = "签名原因", required = true)
    private String signReason;

    /** 前端密码确认（仅校验，不存储） */
    @ApiModelProperty(value = "密码确认（仅校验，不存储）")
    private String password;
}
