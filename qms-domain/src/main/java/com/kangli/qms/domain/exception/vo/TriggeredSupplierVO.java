package com.kangli.qms.domain.exception.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 升级触发供应商 VO（90天内同类不良≥N次的供应商）。
 */
@Data
@ApiModel(description = "升级触发供应商")
public class TriggeredSupplierVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "供应商ID")
    private Long supplierId;

    @ApiModelProperty(value = "供应商名称")
    private String supplierName;

    @ApiModelProperty(value = "供应商编号")
    private String supplierCode;

    @ApiModelProperty(value = "重复不合格物料代码")
    private String materialCode;

    @ApiModelProperty(value = "不良描述")
    private String defectDesc;

    @ApiModelProperty(value = "重复次数")
    private Integer repeatCount;

    @ApiModelProperty(value = "窗口天数")
    private Integer windowDays;

    @ApiModelProperty(value = "是否应升级")
    private Boolean shouldEscalate;

    @ApiModelProperty(value = "关联异常单ID列表")
    private List<Long> relatedExceptionIds;

    /** SQL 返回的逗号分隔 ID 字符串（内部用，不返回前端） */
    @JsonIgnore
    private String relatedExceptionIdsStr;
}
