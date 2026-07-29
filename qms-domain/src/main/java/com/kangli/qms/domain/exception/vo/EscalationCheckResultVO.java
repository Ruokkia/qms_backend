package com.kangli.qms.domain.exception.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量升级检查结果 VO（对齐接口文档 m2-8）。
 */
@Data
@ApiModel(description = "批量升级检查结果")
public class EscalationCheckResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "检查的供应商总数")
    private Integer totalChecked;

    @ApiModelProperty(value = "触发升级的供应商列表")
    private List<TriggeredSupplierVO> triggeredSuppliers;
}
