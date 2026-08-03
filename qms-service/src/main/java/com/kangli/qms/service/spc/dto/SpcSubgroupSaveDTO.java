package com.kangli.qms.service.spc.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * SPC 子组保存请求（手动录入）。
 * <p>sampleValues 数量必须等于参数定义的 subgroupSize。</p>
 */
@Data
public class SpcSubgroupSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联参数 id */
    private Long paramId;
    /** 样本实测值列表 */
    private List<BigDecimal> sampleValues;
    /** 来源类型（默认 手动录入） */
    private String sourceType;
    /** 分类：PRODUCT(产品) / MATERIAL(物料) */
    private String itemType;
    /** 产品/物料代码（随 itemType 取值，控制图关联维度） */
    private String itemCode;
}
