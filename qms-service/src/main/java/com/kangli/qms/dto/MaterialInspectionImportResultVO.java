package com.kangli.qms.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 物料检验批量导入结果 VO。
 */
@Data
@ApiModel(description = "物料检验批量导入结果")
public class MaterialInspectionImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "导入总数")
    private Integer totalCount;

    @ApiModelProperty(value = "成功落库数")
    private Integer successCount;

    @ApiModelProperty(value = "失败数")
    private Integer failCount;

    @ApiModelProperty(value = "失败明细")
    private List<FailItem> failList;

    @ApiModelProperty(value = "自动创建异常单数")
    private Integer createdExceptionCount;

    @ApiModelProperty(value = "自动创建的异常单ID列表")
    private List<Long> createdExceptionIds;

    @Data
    @ApiModel(description = "导入失败明细")
    public static class FailItem implements Serializable {
        private static final long serialVersionUID = 1L;

        @ApiModelProperty(value = "索引")
        private Integer index;

        @ApiModelProperty(value = "记录编号")
        private String recordNo;

        @ApiModelProperty(value = "失败原因")
        private String reason;
    }
}
