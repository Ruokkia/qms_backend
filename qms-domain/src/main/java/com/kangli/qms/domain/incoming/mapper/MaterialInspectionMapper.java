package com.kangli.qms.domain.incoming.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.production.vo.DailyTrendItemVO;
import com.kangli.qms.domain.production.vo.DefectDescItemVO;
import com.kangli.qms.domain.incoming.vo.KeySupplierTrendRowVO;
import com.kangli.qms.domain.incoming.vo.MaterialInspectionStatsVO;
import com.kangli.qms.domain.incoming.vo.SupplierRankItemVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/**
 * 物料检验 Mapper — 含看板统计 + 对账查询的自定义查询。
 * <p>所有统计方法均含 JOIN/CTE 并在 XML 中已手写 plant_code 过滤（带表别名），
 * 故用 {@code @InterceptorIgnore(tenantLine = true)} 跳过分公司拦截器的自动注入，
 * 避免多表列歧义导致 SQL 报错（拦截器仍覆盖继承的 selectById/updateById/deleteById）。</p>
 */
public interface MaterialInspectionMapper extends BaseMapper<MaterialInspection> {

    /** 看板基本统计 */
    @InterceptorIgnore(tenantLine = "true")
    MaterialInspectionStatsVO selectStatsBase(@Param("plantCode") String plantCode);

    /** 高频不合格描述 TOP10 */
    @InterceptorIgnore(tenantLine = "true")
    List<DefectDescItemVO> selectTopDefectDesc(@Param("plantCode") String plantCode);

    /** 供应商合格率排名 */
    @InterceptorIgnore(tenantLine = "true")
    List<SupplierRankItemVO> selectSupplierRank(@Param("plantCode") String plantCode,
                                                @Param("startDate") String startDate,
                                                @Param("endDate") String endDate);

    /** 日统计趋势 */
    @InterceptorIgnore(tenantLine = "true")
    List<DailyTrendItemVO> selectDailyTrend(@Param("plantCode") String plantCode);

    /** 重点供应商 TopN（自定义时间范围内来料批次量最大） */
    @InterceptorIgnore(tenantLine = "true")
    List<SupplierRankItemVO> selectKeySuppliers(@Param("plantCode") String plantCode,
                                                @Param("topN") int topN,
                                                @Param("startDate") String startDate,
                                                @Param("endDate") String endDate);

    /** 重点供应商自定义时间范围每日合格率矩阵（与日期序列外连接，无来料当天为 null） */
    @InterceptorIgnore(tenantLine = "true")
    List<KeySupplierTrendRowVO> selectKeySupplierTrend(@Param("plantCode") String plantCode,
                                                       @Param("topN") int topN,
                                                       @Param("startDate") String startDate,
                                                       @Param("endDate") String endDate);

    /** 同分公司、供应商、物料在指定日期窗口内的不合格批次数 */
    @InterceptorIgnore(tenantLine = "true")
    int countUnqualifiedBatches(@Param("plantCode") String plantCode,
                                @Param("supplierCode") String supplierCode,
                                @Param("materialCode") String materialCode,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);
}
