package com.kangli.qms.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SPC 查表系数实体 — 对应 qms.spc_coefficient 表（AIAG-VDA 标准，固化基线）。
 * <p>n = 2~12 全覆盖，数值不可修改。用于控制限与过程能力计算的系数查表。</p>
 */
@Data
@TableName(value = "spc_coefficient", schema = "qms")
public class SpcCoefficient implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 子组容量 2~12（主键） */
    @TableId
    private Integer n;
    /** Xbar-R 图系数 A2 */
    private BigDecimal a2;
    /** Xbar-s 图系数 A3 */
    private BigDecimal a3;
    /** 估计系数 d2 */
    private BigDecimal d2;
    /** d2 标准差系数 d3 */
    private BigDecimal d3;
    /** 极差图下控制限系数 D3（列名 d3_l） */
    private BigDecimal d3L;
    /** 极差图上控制限系数 D4 */
    private BigDecimal d4;
    /** s 图下控制限系数 B3 */
    private BigDecimal b3;
    /** s 图上控制限系数 B4 */
    private BigDecimal b4;
    /** 估计系数 c4（Xbar-s 短期标准差用） */
    private BigDecimal c4;
}
