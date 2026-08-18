package com.kangli.qms.domain.supplier.vo;

import com.kangli.qms.domain.supplier.entity.SupplierQualification;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商资质 VO（含到期预警天数）。
 */
@Data
@ApiModel(description = "供应商资质（含预警信息）")
public class SupplierQualificationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "资质主体")
    private SupplierQualification qualification;

    @ApiModelProperty(value = "距离到期天数：负数表示已过期，NULL 表示长期有效")
    private Long daysToExpire;

    @ApiModelProperty(value = "预警级别：正常/提醒(90天)/预警(60天)/紧急(30天)/已过期")
    private String warnLevel;
}
