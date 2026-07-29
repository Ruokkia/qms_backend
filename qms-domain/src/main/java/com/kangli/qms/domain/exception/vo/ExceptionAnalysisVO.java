package com.kangli.qms.domain.exception.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 多维度分析结果 VO（对齐接口文档 m2-6 analysis）。
 */
@Data
@ApiModel(description = "多维度分析结果")
public class ExceptionAnalysisVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "分析维度：defectDesc/supplier/material/time")
    private String dimension;

    @ApiModelProperty(value = "聚合结果列表")
    private List<ExceptionAnalysisItemVO> items;
}
