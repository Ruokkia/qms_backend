package com.kangli.qms.domain.supplier.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 资质到期预警汇总 VO（用于定时任务/看板）。
 */
@Data
@ApiModel(description = "资质到期预警项")
public class SupplierQualificationExpiryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "资质ID")
    private Long qualificationId;

    @ApiModelProperty(value = "供应商ID")
    private Long supplierId;

    @ApiModelProperty(value = "供应商名称")
    private String supplierName;

    @ApiModelProperty(value = "资质类型")
    private String certType;

    @ApiModelProperty(value = "证照编号")
    private String certNo;

    @ApiModelProperty(value = "到期日期")
    private String expireDate;

    @ApiModelProperty(value = "距离到期天数（负数=已过期）")
    private Long daysToExpire;

    @ApiModelProperty(value = "预警级别：提醒/预警/紧急/已过期")
    private String warnLevel;

    @ApiModelProperty(value = "分公司编码")
    private String plantCode;

    @ApiModelProperty(value = "分公司名称")
    private String plantName;
}
