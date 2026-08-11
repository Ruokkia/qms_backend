package com.kangli.qms.service.fai.dto;

import java.util.List;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 首件检验标准模板 保存请求 DTO —— 用于标准的新增/更新。
 * <p>含物料代码/名称、工序、版本、是否激活、备注及参数项明细列表。</p>
 */
@Data
@ApiModel(value = "FaiStandardSaveRequest", description = "首件检验标准模板保存请求")
public class FaiStandardSaveRequest {

    @ApiModelProperty(value = "标准 ID（更新时必填，新建时不填）")
    private Long id;

    @ApiModelProperty(value = "物料代码", required = true)
    private String materialCode;

    @ApiModelProperty(value = "物料名称（便于展示）")
    private String materialName;

    @ApiModelProperty(value = "分类：PRODUCT(产品)/MATERIAL(物料)", required = true)
    private String itemType;

    @ApiModelProperty(value = "产品/物料条码（追溯标识）")
    private String itemBarcode;

    @ApiModelProperty(value = "产品/物料代码（随 itemType 取值）")
    private String itemCode;

    @ApiModelProperty(value = "产品/物料名称（随 itemType 取值）")
    private String itemName;

    @ApiModelProperty(value = "工序：装配/焊接/检测", required = true)
    private String processName;
    private String processCode;

    @ApiModelProperty(value = "标准版本号（缺省自动计算下一版本）")
    private Integer stdVersion;

    @ApiModelProperty(value = "是否激活：是/否（缺省为是）")
    private String isActive;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "生效日期（ECN 变更/药监审计）")
    private String effectiveDate;

    @ApiModelProperty(value = "变更备注（ECN 变更/药监审计）")
    private String changeRemark;

    @ApiModelProperty(value = "参数项明细列表", required = true)
    private List<FaiStandardItemRequest> items;
}
