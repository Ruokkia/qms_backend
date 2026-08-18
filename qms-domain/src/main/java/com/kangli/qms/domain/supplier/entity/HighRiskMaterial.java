package com.kangli.qms.domain.supplier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;

/**
 * 高风险物料清单（固化数据，仅数据库维护）。
 * 对涉及的高风险物料，供应商除常规审核外还需提交额外资料要求。
 */
@Data
@TableName(value = "high_risk_material", schema = "qms")
public class HighRiskMaterial implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 风险等级：高/中/低 */
    private String riskLevel;
    /** 额外资料要求说明（如：提供材质证明、RoHS 报告、过程能力研究等） */
    private String extraRequirement;
    /** 备注 */
    private String remark;
}
