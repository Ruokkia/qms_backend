package com.kangli.qms.dto;

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

    @ApiModelProperty(value = "工单号")
    private String workOrderNo;

    @ApiModelProperty(value = "物料代码")
    private String materialCode;

    @ApiModelProperty(value = "物料名称")
    private String materialName;

    @ApiModelProperty(value = "批次号")
    private String batchNo;

    @ApiModelProperty(value = "工序：装配/焊接/检测")
    private String processName;
    private String processCode;

    @ApiModelProperty(value = "触发原因")
    private String triggerReason;

    @ApiModelProperty(value = "备注")
    private String remark;
}
