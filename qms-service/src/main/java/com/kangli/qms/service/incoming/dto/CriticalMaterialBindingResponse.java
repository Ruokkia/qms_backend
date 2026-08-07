package com.kangli.qms.service.incoming.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 关键物料绑定响应 DTO（红线 #2：实体不出 Service 边界）。
 */
@Data
@ApiModel(value = "CriticalMaterialBindingResponse", description = "关键物料绑定响应")
public class CriticalMaterialBindingResponse {

    @ApiModelProperty("主键")
    private Long id;
    @ApiModelProperty("类别")
    private String category;
    @ApiModelProperty("工单号")
    private String workOrderNo;
    @ApiModelProperty("产品条码/SN")
    private String productBarcode;
    @ApiModelProperty("产品物料号")
    private String productMaterialNo;
    @ApiModelProperty("产品名称")
    private String productName;
    @ApiModelProperty("工单数量")
    private BigDecimal workOrderQty;
    @ApiModelProperty("物料条码/SN")
    private String materialBarcode;
    @ApiModelProperty("物料代码")
    private String materialCode;
    @ApiModelProperty("物料名称")
    private String materialName;
    @ApiModelProperty("子项批号（详情展示用）")
    private String sonLotNo;
    @ApiModelProperty("规格型号")
    private String specModel;
    @ApiModelProperty("扫描人")
    private String scanner;
    @ApiModelProperty("扫描时间")
    private LocalDateTime scanTime;
    @ApiModelProperty("工序代码")
    private String processCode;
    @ApiModelProperty("工序名称：装配/焊接/检测")
    private String processName;
    @ApiModelProperty("是否生效：是/否")
    private String isActive;
    @ApiModelProperty("失效操作人")
    private String deactivateOperator;
    @ApiModelProperty("失效时间")
    private LocalDateTime deactivateTime;
    @ApiModelProperty("备注")
    private String remark;
    @ApiModelProperty("分公司编码")
    private String plantCode;
    @ApiModelProperty("分公司名称")
    private String plantName;
    @ApiModelProperty("创建人")
    private String createdBy;
    @ApiModelProperty("更新人")
    private String updatedBy;
    @ApiModelProperty("版本号")
    private Integer version;
    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;
    @ApiModelProperty("更新时间")
    private LocalDateTime updatedAt;
}
