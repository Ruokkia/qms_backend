package com.kangli.qms.service.incoming.dto;

import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 物料检验批量导入请求 DTO。
 */
@Data
@ApiModel(description = "物料检验批量导入请求")
public class MaterialInspectionImportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "物料检验记录列表", required = true)
    private List<MaterialInspection> list;

    @ApiModelProperty(value = "是否自动建异常单，默认 true")
    private Boolean autoCreateException;
}
