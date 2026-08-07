package com.kangli.qms.service.fai.dto;

import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.fai.entity.FaiSignature;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 首件检验主记录响应（M3，含参数明细与签名列表）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "首件检验主记录响应")
public class FaiInspectionRecordResponse extends FaiInspectionRecord {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "参数明细列表")
    private List<FaiInspectionItemResponse> items;

    @ApiModelProperty(value = "电子签名列表")
    private List<FaiSignature> signatures;

    @ApiModelProperty(value = "电子签名完整性：true=已签且内容哈希一致；false=未签/被篡改/历史遗留")
    private Boolean signatureIntact;

    @ApiModelProperty(value = "历史遗留签名标识：true=该签名无内容绑定哈希（无法做完整性复核），按祖父条款认可")
    private Boolean legacySignature;
}
