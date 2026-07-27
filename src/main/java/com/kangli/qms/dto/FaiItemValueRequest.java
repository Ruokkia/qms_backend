package com.kangli.qms.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 批量提交检验参数实际值请求（M3）。
 */
@Data
@ApiModel(description = "批量提交检验参数实际值请求")
public class FaiItemValueRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "首件检验记录ID不能为空")
    @ApiModelProperty(value = "首件检验记录ID", required = true)
    private Long faiRecordId;

    @Valid
    @ApiModelProperty(value = "参数实际值列表")
    private List<ItemValue> items;

    /** 单项实际值 */
    @Data
    @ApiModel(description = "单项实际值")
    public static class ItemValue implements Serializable {
        private static final long serialVersionUID = 1L;

        @ApiModelProperty(value = "明细项ID")
        private Long id;

        @ApiModelProperty(value = "实际值")
        private BigDecimal actualValue;
    }
}
