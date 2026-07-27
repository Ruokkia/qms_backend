package com.kangli.qms.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 追溯节点关联的批次信息 VO（对齐前端 TraceBatchInfo 类型）。
 */
@Data
@ApiModel(description = "追溯批次信息")
public class TraceBatchInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "批次号")
    private String batchNo;

    @ApiModelProperty(value = "供应商名称")
    private String supplierName;

    @ApiModelProperty(value = "供应商编号")
    private String supplierCode;

    @ApiModelProperty(value = "物料编码")
    private String materialCode;

    @ApiModelProperty(value = "物料名称")
    private String materialName;

    @ApiModelProperty(value = "规格型号")
    private String specModel;

    @ApiModelProperty(value = "IQC 检验状态：待检/在检/已检/异常")
    private String iqcStatus;

    @ApiModelProperty(value = "检验结果：合格/不合格")
    private String inspectionResult;

    @ApiModelProperty(value = "来料日期")
    private String arrivalDate;

    @ApiModelProperty(value = "数量")
    private BigDecimal qty;

    @ApiModelProperty(value = "不合格描述")
    private String defectDesc;

    /**
     * material_inspection.id（内部匹配用，不返回前端）。
     */
    @JsonIgnore
    private Long batchId;
}
