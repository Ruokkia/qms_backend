package com.kangli.qms.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 生产维修 Excel 导入结果 VO。
 */
@Data
@ApiModel(description = "Excel导入结果")
public class ProductionRepairImportResultVO {

    @ApiModelProperty("导入总数")
    private int totalCount;
    @ApiModelProperty("成功数")
    private int successCount;
    @ApiModelProperty("重复跳过数")
    private int duplicateSkipCount;
    @ApiModelProperty("待补全数据数（本次导入中标记为 PENDING_COMPLETION 的条数）")
    private int pendingCount;
    @ApiModelProperty("失败数")
    private int failCount;
    @ApiModelProperty("失败行详情")
    private List<FailItem> failList;

    @Data
    @ApiModel(description = "导入失败明细")
    public static class FailItem {
        @ApiModelProperty("行号（从 1 开始，不含表头）")
        private int rowIndex;
        @ApiModelProperty("维修编号（若有）")
        private String repairNo;
        @ApiModelProperty("失败原因")
        private String reason;
    }
}
