package com.kangli.qms.service.supplier.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 供应商资质新增/更新 DTO。
 */
@Data
@ApiModel(description = "供应商资质新增/更新")
public class SupplierQualificationCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "供应商ID")
    private Long supplierId;

    @ApiModelProperty(value = "资质类型：营业执照/生产许可证/ISO9001/ISO13485/医疗器械经营许可证/其他")
    private String certType;

    @ApiModelProperty(value = "证照编号")
    private String certNo;

    @ApiModelProperty(value = "发证机构")
    private String issuer;

    @ApiModelProperty(value = "签发日期，格式 yyyy-MM-dd")
    private String issueDate;

    @ApiModelProperty(value = "到期日期，格式 yyyy-MM-dd；长期有效时留空")
    private String expireDate;

    @ApiModelProperty(value = "是否长期有效：true=是（不预警）")
    private Boolean longTerm;

    @ApiModelProperty(value = "附件 URL 列表（逗号分隔存储）")
    private List<String> fileUrls;

    @ApiModelProperty(value = "备注")
    private String remark;
}
