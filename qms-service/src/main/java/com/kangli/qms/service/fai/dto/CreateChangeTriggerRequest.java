package com.kangli.qms.service.fai.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 创建变更触发请求（M3）。
 */
@Data
@ApiModel(description = "创建变更触发请求")
public class CreateChangeTriggerRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "变更类型不能为空")
    @ApiModelProperty(value = "变更类型：换模具/升级系统/换批次/换设备/材料批次", required = true)
    private String triggerType;

    @NotBlank(message = "分类不能为空")
    @ApiModelProperty(value = "分类：PRODUCT(产品) / MATERIAL(物料)", required = true)
    private String itemType;

    @ApiModelProperty(value = "产品/物料代码（随 itemType 取值）")
    private String itemCode;

    @ApiModelProperty(value = "产品/物料名称（随 itemType 取值）")
    private String itemName;

    @ApiModelProperty(value = "产品/物料条码（随 itemType 取值）")
    private String itemBarcode;

    @ApiModelProperty(value = "批次号")
    private String batchNo;

    @ApiModelProperty(value = "物料代码（冗余兼容列）")
    private String materialCode;

    @ApiModelProperty(value = "物料名称（冗余兼容列）")
    private String materialName;

    @ApiModelProperty(value = "工序：装配/焊接/检测")
    private String processName;
    private String processCode;

    @ApiModelProperty(value = "触发原因")
    private String triggerReason;

    @ApiModelProperty(value = "备注")
    private String remark;
}
