package com.kangli.qms.common;

import lombok.Data;

import java.io.Serializable;

/**
 * SPC 统一代码字典项。
 * <p>权威源 = 已签首件（FAI 记录，signature_status='已签' 且 inspection_result='合格'）
 * ∪ 已激活标准（FAI 标准，is_active='是'），按 (itemType, itemCode) 去重。</p>
 * <p>用于 SPC 数据采集 / 控制图 / 标准维护的代码模糊搜索，彻底脱离 trace 业务表，
 * 消除 Demo 占位码与真实业务数据两套体系冲突。</p>
 */
@Data
public class SpcItemDictDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分类 PRODUCT(产品) / MATERIAL(物料) */
    private String itemType;

    /** 产品/物料代码 */
    private String itemCode;

    /** 产品/物料名称（来自统一字典，便于回填） */
    private String itemName;

    /** 统一工序库编码（来自首个来源记录） */
    private String processCode;

    /** 参数编码（来源记录中的 param_code 拼接，逗号分隔） */
    private String paramCode;
}
