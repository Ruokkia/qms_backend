package com.kangli.qms.service.fai.dto;

import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.fai.entity.FaiSignature;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 首件检验报告响应（M3，JSON 报告视图）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "首件检验报告响应")
public class FaiReportResponse extends FaiInspectionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "参数明细列表")
    private List<FaiInspectionItemResponse> items;

    @ApiModelProperty(value = "电子签名列表")
    private List<FaiSignature> signatures;

    @ApiModelProperty(value = "参数总项数")
    private Integer totalCount;

    @ApiModelProperty(value = "合格项数")
    private Integer qualifiedCount;

    @ApiModelProperty(value = "不合格项数")
    private Integer unqualifiedCount;

    @ApiModelProperty(value = "参数合格率（0~100，保留两位小数）")
    private BigDecimal passRate;
}
