package com.kangli.qms.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 内部创建通知 DTO（后端调用，不暴露给前端 Swagger）。
 */
@Data
@ApiModel(description = "创建通知请求（内部）")
public class NotificationCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "接收人ID不能为空")
    @ApiModelProperty(value = "接收人ID", required = true)
    private Long userId;

    @NotBlank(message = "通知类型不能为空")
    @ApiModelProperty(value = "通知类型", required = true)
    private String type;

    @NotBlank(message = "标题不能为空")
    @ApiModelProperty(value = "标题", required = true)
    private String title;

    @NotBlank(message = "内容不能为空")
    @ApiModelProperty(value = "内容", required = true)
    private String content;

    @ApiModelProperty(value = "通知等级：严重/警告/提醒")
    private String level;

    @ApiModelProperty(value = "业务类型")
    private String businessType;

    @ApiModelProperty(value = "业务ID")
    private Long businessId;

    @ApiModelProperty(value = "分公司编码")
    private String plantCode;

    @ApiModelProperty(value = "创建人")
    private String createdBy;
}
