package com.kangli.qms.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 物料检验对账结果 VO（兜底直写库/ETL）。
 */
@Data
@ApiModel(description = "物料检验对账结果")
public class MaterialInspectionReconcileResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "扫描到的不合格记录数")
    private Integer scannedCount;

    @ApiModelProperty(value = "本次新建的异常单数")
    private Integer createdCount;

    @ApiModelProperty(value = "新建异常单ID列表")
    private List<Long> createdExceptionIds;
}
