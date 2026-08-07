package com.kangli.qms.service.finishedgoods.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 成品入库检验响应 DTO（红线 #2：实体不出 Service 边界）。
 */
@Data
@ApiModel(value = "FinishedGoodsInspectionResponse", description = "成品入库检验响应")
public class FinishedGoodsInspectionResponse {

    @ApiModelProperty("主键")
    private Long id;
    @ApiModelProperty("是否加急：是/否")
    private String isUrgent;
    @ApiModelProperty("品管审核：待审核/已审核/驳回")
    private String qcReview;
    @ApiModelProperty("管代批准：待审核/已审核/驳回")
    private String mgrApproval;
    @ApiModelProperty("是否有效：是/否")
    private String isValid;
    @ApiModelProperty("检验结果：合格/不合格")
    private String inspectionResult;
    @ApiModelProperty("报告编号")
    private String reportNo;
    @ApiModelProperty("检验申请单号")
    private String inspectionRequestNo;
    @ApiModelProperty("生产订单号")
    private String productionOrderNo;
    @ApiModelProperty("物料代码")
    private String materialCode;
    @ApiModelProperty("产品名称")
    private String productName;
    @ApiModelProperty("型号规格")
    private String modelSpec;
    @ApiModelProperty("生产批号/SN")
    private String prodBatchOrSn;
    @ApiModelProperty("生产日期")
    private LocalDate productionDate;
    @ApiModelProperty("有效期至")
    private LocalDate expiryDate;
    @ApiModelProperty("送检数量")
    private BigDecimal submittedQty;
    @ApiModelProperty("检验数量")
    private BigDecimal inspectedQty;
    @ApiModelProperty("合格数量")
    private BigDecimal qualifiedQty;
    @ApiModelProperty("不合格数量")
    private BigDecimal unqualifiedQty;
    @ApiModelProperty("单位")
    private String unit;
    @ApiModelProperty("检验员")
    private String inspectorName;
    @ApiModelProperty("类别")
    private String category;
    @ApiModelProperty("品管审核人")
    private String qcReviewer;
    @ApiModelProperty("品管审核时间")
    private LocalDateTime qcReviewTime;
    @ApiModelProperty("管代批准人")
    private String mgrRepresentative;
    @ApiModelProperty("管代批准时间")
    private LocalDateTime mgrApprovalTime;
    @ApiModelProperty("是否受托生产：是/否")
    private String isEntrusted;
    @ApiModelProperty("药品注册证号")
    private String drugRegNo;
    @ApiModelProperty("性能检验方法")
    private String perfTestMethod;
    @ApiModelProperty("性能检验批号")
    private String perfSampleBatchNo;
    @ApiModelProperty("电子签名用户")
    private String signatureUser;
    @ApiModelProperty("电子签名时间")
    private LocalDateTime signatureTime;
    @ApiModelProperty("电子签名事由")
    private String signatureReason;
    @ApiModelProperty("分公司编码")
    private String plantCode;
    @ApiModelProperty("分公司名称")
    private String plantName;
    @ApiModelProperty("创建人")
    private String createdBy;
    @ApiModelProperty("更新人")
    private String updatedBy;
    @ApiModelProperty("版本号")
    private Integer version;
    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;
    @ApiModelProperty("更新时间")
    private LocalDateTime updatedAt;
}
