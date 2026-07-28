package com.kangli.qms.dto;

import com.kangli.qms.entity.FaiInspectionStandard;
import com.kangli.qms.entity.FaiInspectionStandardItem;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 首件检验标准模板响应（M3，含参数项）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "首件检验标准模板响应")
public class FaiStandardResponse extends FaiInspectionStandard implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "标准模板参数项列表")
    private List<FaiInspectionStandardItem> items;
}
