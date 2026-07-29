package com.kangli.qms.service.exception.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/** 发起供应商升级的受控输入。供应商主数据和初始流程状态由服务端确定。 */
@Data
public class EscalationCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "供应商不能为空")
    private Long supplierId;

    @NotBlank(message = "升级原因不能为空")
    private String escalationReason;

    @NotBlank(message = "升级动作不能为空")
    private String escalationAction;

    /** 关联物料编码；从升级检查创建时由触发记录带入。 */
    private String materialCode;

    private String relatedExceptionIds;
    private String remark;
}
