package com.kangli.qms.service.supplier;

import com.kangli.qms.domain.supplier.vo.SupplierPerfParetoVO;
import com.kangli.qms.domain.supplier.vo.SupplierPerfTrendVO;
import com.kangli.qms.domain.supplier.vo.SupplierPerformanceVO;

import java.util.List;

/**
 * 供应商绩效评审业务接口。
 */
public interface SupplierPerformanceService {

    /** 全维度绩效排名（含综合评分与 A/B/C/D 等级） */
    List<SupplierPerformanceVO> rank();

    /** 月度绩效趋势（平均分/合格率） */
    List<SupplierPerfTrendVO> trend();

    /** 不合格柏拉图（按缺陷类型分布 + 累计占比） */
    List<SupplierPerfParetoVO> pareto();
}
