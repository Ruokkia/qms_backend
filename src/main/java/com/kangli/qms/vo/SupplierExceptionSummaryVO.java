package com.kangli.qms.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 供应商来料不良频次汇总 VO。
 */
@Data
@ApiModel(description = "供应商来料不良频次汇总")
public class SupplierExceptionSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "供应商ID")
    private Long supplierId;

    @ApiModelProperty(value = "供应商名称")
    private String supplierName;

    @ApiModelProperty(value = "供应商编号")
    private String supplierCode;

    @ApiModelProperty(value = "来料不良异常单发生次数")
    private Integer occurrenceCount;

    @ApiModelProperty(value = "关联异常单ID列表")
    private List<Long> relatedExceptionIds;

    @ApiModelProperty(value = "最近发生时间")
    private String latestOccurrenceAt;

    @ApiModelProperty(value = "最高频不良描述")
    private String topDefectDesc;

    /** 数据库原始逗号分隔字符串（内部使用，不返回前端） */
    private String relatedExceptionIdsStr;
}

