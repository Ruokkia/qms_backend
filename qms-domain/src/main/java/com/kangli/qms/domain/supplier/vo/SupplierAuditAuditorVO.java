package com.kangli.qms.domain.supplier.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商现场审核审核人下拉选项 VO。
 */
@Data
@ApiModel(description = "审核人下拉选项")
public class SupplierAuditAuditorVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "用户 ID")
    private Long id;

    @ApiModelProperty(value = "账号")
    private String account;

    @ApiModelProperty(value = "姓名")
    private String realName;

    @ApiModelProperty(value = "角色编码")
    private String roleCode;
}
