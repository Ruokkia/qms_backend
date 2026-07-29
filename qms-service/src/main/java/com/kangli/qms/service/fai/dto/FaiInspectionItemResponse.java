package com.kangli.qms.service.fai.dto;

import com.kangli.qms.domain.fai.entity.FaiInspectionItem;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 首件检验参数明细响应（M3）。
 * <p>扩展字段用于展示最新标准值对比与同步提示。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "首件检验参数明细响应")
public class FaiInspectionItemResponse extends FaiInspectionItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否必检（由标准模板关联，冗余便于前端展示；非必检项判定不强制） */
    @ApiModelProperty(value = "是否必检：是/否")
    private String isRequired;

    /** 当前激活标准中的最新标准值（用于对比提示） */
    @ApiModelProperty(value = "最新标准值")
    private String latestStandardValue;

    /** 当前激活标准中的最新上限 */
    @ApiModelProperty(value = "最新上限")
    private BigDecimal latestUpperLimit;

    /** 当前激活标准中的最新下限 */
    @ApiModelProperty(value = "最新下限")
    private BigDecimal latestLowerLimit;

    /** 当前激活标准中的最新单位 */
    @ApiModelProperty(value = "最新单位")
    private String latestUnit;

    /** 标准是否已变更（标准值/上下限/单位任一不一致即为 true） */
    @ApiModelProperty(value = "标准是否已变更")
    private Boolean hasStandardChanged;
}
