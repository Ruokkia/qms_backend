package com.kangli.qms.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户信息 VO（与前端 UserInfo 类型对齐）。
 */
@Data
@ApiModel(description = "用户信息")
public class UserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "用户主键ID", example = "1")
    private Long userId;

    @ApiModelProperty(value = "登录账号", example = "sz_op01")
    private String account;

    @ApiModelProperty(value = "真实姓名", example = "张三")
    private String realName;

    @ApiModelProperty(value = "角色编码", example = "R01")
    private String roleCode;

    @ApiModelProperty(value = "角色名称", example = "操作工")
    private String roleName;

    @ApiModelProperty(value = "分公司编码", example = "SZ")
    private String plantCode;

    @ApiModelProperty(value = "分公司名称", example = "深圳")
    private String plantName;

    @ApiModelProperty(value = "状态 1=启用 0=禁用", example = "1")
    private Short status;

    @ApiModelProperty(value = "最后登录时间", example = "2026-07-17 10:30:00")
    private String lastLoginAt;
}
