package com.kangli.qms.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 重点供应商质量趋势 VO（近30天来料批次量 Top5）。
 * <p>重点供应商 = 近30天来料批次量最大的 5 家供应商。</p>
 */
@Data
@ApiModel(description = "重点供应商质量趋势（近30天批次量Top5）")
public class KeySupplierTrendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "重点供应商 Top5 元信息")
    private List<KeySupplierItemVO> keySuppliers;

    @ApiModelProperty(value = "统一日期轴（近30天，YYYY-MM-DD，升序）")
    private List<String> dates;

    @ApiModelProperty(value = "每家供应商对齐日期轴的合格率序列")
    private List<KeySupplierSeriesVO> series;

    /** 重点供应商项 */
    @Data
    @ApiModel(description = "重点供应商项")
    public static class KeySupplierItemVO implements Serializable {
        private static final long serialVersionUID = 1L;
        @ApiModelProperty(value = "供应商编号")
        private String supplierCode;
        @ApiModelProperty(value = "供应商名称")
        private String supplierName;
        @ApiModelProperty(value = "近30天来料批次数")
        private Integer totalBatches;
        @ApiModelProperty(value = "近30天整体合格率（%）")
        private BigDecimal passRate;
    }

    /** 重点供应商趋势线 */
    @Data
    @ApiModel(description = "重点供应商趋势线")
    public static class KeySupplierSeriesVO implements Serializable {
        private static final long serialVersionUID = 1L;
        @ApiModelProperty(value = "供应商编号")
        private String supplierCode;
        @ApiModelProperty(value = "供应商名称")
        private String supplierName;
        @ApiModelProperty(value = "与 dates 对齐的每日合格率（%），无来料当天为 null")
        private List<BigDecimal> passRateList;
    }
}
