package com.kangli.qms.domain.exception.vo;

import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.entity.ImprovementAction;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.exception.entity.RectificationPlan;
import com.kangli.qms.domain.exception.entity.VerificationRecord;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 异常单详情 VO（对齐接口文档 m2-2 详情，含改善措施+验证记录+8D+关联来料）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "异常单详情（含改善措施+验证记录+8D+关联来料）")
public class ExceptionDetailVO extends ExceptionOrder {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "供应商名称（连表填充）")
    private String supplierName;

    @ApiModelProperty(value = "审核人姓名（连表填充）")
    private String reviewerName;

    @ApiModelProperty(value = "改善措施列表")
    private List<ImprovementAction> improvementActions;

    @ApiModelProperty(value = "验证记录列表")
    private List<VerificationRecord> verificationRecords;

    @ApiModelProperty(value = "整改计划列表（与改善措施区分的独立对象）")
    private List<RectificationPlan> rectificationPlans;

    @ApiModelProperty(value = "关联 8D 报告")
    private EightDVO eightD;

    @ApiModelProperty(value = "已发送通知数")
    private Integer notificationCount;

    @ApiModelProperty(value = "关联来料检验记录（仅 sourceType=来料不良 时填充）")
    private MaterialInspection materialInspection;

    @ApiModelProperty(value = "关联首件检验记录（仅 sourceType=首件不良 时填充）")
    private FaiInspectionRecord faiInspection;

    @ApiModelProperty(value = "关联成品检验记录（仅 sourceType=成品不良 时填充）")
    private FinishedGoodsInspection finishedGoodsInspection;
}

