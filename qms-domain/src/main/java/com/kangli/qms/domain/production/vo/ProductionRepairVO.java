package com.kangli.qms.domain.production.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产维修记录 展示 VO（禁止直接返回实体）。
 */
@Data
@ApiModel(description = "生产维修记录")
public class ProductionRepairVO {

    @ApiModelProperty("主键")
    private Long id;
    @ApiModelProperty("维修编号")
    private String repairNo;
    @ApiModelProperty("表格名称")
    private String formName;
    @ApiModelProperty("表格编号")
    private String formNo;
    @ApiModelProperty("产品编号")
    private String productNo;
    @ApiModelProperty("产品名称")
    private String productName;
    @ApiModelProperty("规格型号")
    private String specModel;
    @ApiModelProperty("生产工单号")
    private String workOrderNo;
    @ApiModelProperty("产品批号/序列号")
    private String productBatchOrSn;
    @ApiModelProperty("生产工序（自由文本）")
    private String process;
    @ApiModelProperty("不良数量")
    private BigDecimal defectQty;
    @ApiModelProperty("不良现象")
    private String defectPhenomenon;
    @ApiModelProperty("不良代码（历史自由文本，非统计维度）")
    private String defectCode;
    @ApiModelProperty("送修日期")
    private LocalDate sendRepairDate;
    @ApiModelProperty("维修日期")
    private LocalDate repairDate;
    @ApiModelProperty("维修判定结果")
    private String repairJudgmentResult;
    @ApiModelProperty("状态：（自由文本）")
    private String repairStatus;
    @ApiModelProperty("维修记录")
    private String repairRecord;
    @ApiModelProperty("送修人")
    private String sendRepairer;
    @ApiModelProperty("维修人")
    private String repairer;
    @ApiModelProperty("审核人")
    private String auditor;
    @ApiModelProperty("审核状态")
    private String auditStatus;
    @ApiModelProperty("审核日期")
    private LocalDate auditDate;
    @ApiModelProperty("备注")
    private String remark;
    @ApiModelProperty("维修状态 0=未维修 1=已维修")
    private Integer repairDone;
    @ApiModelProperty("分公司编码")
    private String plantCode;
    @ApiModelProperty("分公司名称")
    private String plantName;
    @ApiModelProperty("创建人")
    private String createdBy;
    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;
    @ApiModelProperty("更新时间")
    private LocalDateTime updatedAt;
}
