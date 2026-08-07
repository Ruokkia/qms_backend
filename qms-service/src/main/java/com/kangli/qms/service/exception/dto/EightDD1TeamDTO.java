package com.kangli.qms.service.exception.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.List;

/**
 * D1 团队提交请求（负责人自行组建团队后提交质量部审核）。
 */
@Data
@ApiModel(description = "D1 团队提交请求")
public class EightDD1TeamDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "团队成员不能为空")
    @ApiModelProperty(value = "团队成员列表", required = true)
    private List<D1MemberItem> memberList;

    @ApiModelProperty(value = "CAPA 负责人姓名列表（逗号分隔，选 BOTH 时填写）", required = false)
    private String capaOwner;

    @ApiModelProperty(value = "版本号（乐观锁）", required = true)
    private Integer version;

    /**
     * D1 团队成员项。
     */
    @Data
    @ApiModel(description = "D1 团队成员项")
    public static class D1MemberItem implements Serializable {
        private static final long serialVersionUID = 1L;

        @ApiModelProperty(value = "用户ID")
        private Long userId;

        @ApiModelProperty(value = "真实姓名")
        private String realName;

        @ApiModelProperty(value = "角色编码")
        private String roleCode;
    }
}
