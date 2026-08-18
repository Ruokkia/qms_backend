package com.kangli.qms.service.supplier.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 提交供应商物料变更申请请求。
 */
@Data
@ApiModel(description = "提交供应商物料变更申请请求")
public class SupplierMaterialChangeCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "供应商编码不能为空")
    @ApiModelProperty(value = "供应商编码", required = true)
    private String supplierCode;

    @NotBlank(message = "供应商名称不能为空")
    @ApiModelProperty(value = "供应商名称", required = true)
    private String supplierName;

    @NotBlank(message = "物料编码不能为空")
    @ApiModelProperty(value = "物料编码", required = true)
    private String materialCode;

    @NotBlank(message = "物料名称不能为空")
    @ApiModelProperty(value = "物料名称", required = true)
    private String materialName;

    @NotBlank(message = "变更类型不能为空")
    @ApiModelProperty(value = "变更类型：SPEC 规格 / PROCESS 工艺 / ORIGIN 产地", required = true)
    private String changeType;

    @NotBlank(message = "变更说明不能为空")
    @ApiModelProperty(value = "变更说明", required = true)
    private String changeDesc;

    @ApiModelProperty(value = "验证报告（文本+附件URL JSON）")
    private String validationReport;

    @ApiModelProperty(value = "风险评估")
    private String riskAssessment;

    @ApiModelProperty(value = "加严子组样本数（SPC 子组大小）")
    private Integer tightenedSubgroupSize;

    @ApiModelProperty(value = "是否联动 SPC")
    private Boolean spcEnabled;

    @ApiModelProperty(value = "附件 URL JSON")
    private String attachments;

    @ApiModelProperty(value = "质量审批人用户ID", required = true)
    private Long qualityApproverId;

    @ApiModelProperty(value = "采购审批人用户ID", required = true)
    private Long purchaseApproverId;

    @ApiModelProperty(value = "研发审批人用户ID", required = true)
    private Long rdApproverId;
}
