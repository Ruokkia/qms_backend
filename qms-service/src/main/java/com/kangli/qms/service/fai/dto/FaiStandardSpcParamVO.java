package com.kangli.qms.service.fai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * SPC 数据采集专用参数 VO（来源于 FAI 检验标准项 + SPC 参数字典补全）。
 * <p>工序和参数数据源从旧的 SPC 字典表切换为 FAI 参数标准层，
 * 该 VO 用于前端数据采集页面的参数下拉和详情卡片展示。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaiStandardSpcParamVO {

    /** FAI 标准参数项 ID */
    private Long standardItemId;

    /** SPC 参数 ID（提交子组时的 paramId） */
    private Long spcParameterId;

    /** 参数名称 */
    private String paramName;

    /** 参数编码 */
    private String paramCode;

    /** 规格上限 USL */
    private BigDecimal upperLimit;

    /** 规格下限 LSL */
    private BigDecimal lowerLimit;

    /** 目标值 */
    private String standardValue;

    /** 单位 */
    private String unit;

    /** 子组大小 n */
    private Integer subgroupSize;

    /** 控制图类型：Xbar-R / Xbar-s */
    private String chartType;
}
