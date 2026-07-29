package com.kangli.qms.domain.incoming.vo;

import com.kangli.qms.domain.production.vo.DailyTrendItemVO;
import com.kangli.qms.domain.production.vo.DefectDescItemVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 物料检验看板统计 VO（对齐接口文档 m1-2）。
 */
@Data
@ApiModel(description = "来料检验看板统计")
public class MaterialInspectionStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "总批次数")
    private Integer totalBatches;

    @ApiModelProperty(value = "合格批次数")
    private Integer qualifiedBatches;

    @ApiModelProperty(value = "不合格批次数")
    private Integer unqualifiedBatches;

    @ApiModelProperty(value = "合格率（%）")
    private BigDecimal qualifiedRate;

    @ApiModelProperty(value = "待审核数")
    private Integer pendingReviewCount;

    @ApiModelProperty(value = "急料数")
    private Integer urgentCount;

    @ApiModelProperty(value = "高频不合格描述 TOP10")
    private List<DefectDescItemVO> topDefectDesc;

    @ApiModelProperty(value = "供应商合格率排名")
    private List<SupplierRankItemVO> supplierRank;

    @ApiModelProperty(value = "日统计趋势")
    private List<DailyTrendItemVO> dailyTrend;
}
