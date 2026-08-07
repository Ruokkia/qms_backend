package com.kangli.qms.domain.exception.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 人员选项 VO（发起整改 / 指派团队 / 选择责任人时使用）。
 * 仅暴露指派所需的精简字段，避免返回账号、密码版本等敏感信息。
 */
@Data
@ApiModel(description = "人员选项（责任人 / 8D 团队 / CAPA 负责人选择）")
public class ExceptionUserOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "用户 ID")
    private Long id;

    @ApiModelProperty(value = "姓名")
    private String realName;

    @ApiModelProperty(value = "角色编码（R00-R06）")
    private String roleCode;

    @ApiModelProperty(value = "分公司编码（SZ/MZ）")
    private String plantCode;
}
