package com.kangli.qms.domain.exception.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.production.vo.BreakdownItemVO;
import com.kangli.qms.domain.exception.vo.ExceptionAnalysisItemVO;
import com.kangli.qms.domain.exception.vo.ExceptionStatsVO;
import com.kangli.qms.domain.supplier.vo.SupplierExceptionSummaryVO;
import com.kangli.qms.domain.exception.vo.TriggeredSupplierVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 异常单 Mapper — 含统计分析 + 重复问题升级检查 + 供应商频次汇总的自定义查询。
 * <p>所有统计方法均含 JOIN/子查询并在 XML 中已手写 plant_code 过滤（带表别名），
 * 故用 {@code @InterceptorIgnore(tenantLine = true)} 跳过分公司拦截器的自动注入，
 * 避免多表列歧义导致 SQL 报错（拦截器仍覆盖继承的 selectById/updateById/deleteById）。</p>
 */
public interface ExceptionOrderMapper extends BaseMapper<ExceptionOrder> {

    /**
     * KPI 看板统计。
     */
    @InterceptorIgnore(tenantLine = "true")
    ExceptionStatsVO selectStats(@Param("plantCode") String plantCode);

    /**
     * 按严重等级分组统计。
     */
    @InterceptorIgnore(tenantLine = "true")
    List<BreakdownItemVO> selectSeverityBreakdown(@Param("plantCode") String plantCode);

    /**
     * 按来源类型分组统计。
     */
    @InterceptorIgnore(tenantLine = "true")
    List<BreakdownItemVO> selectSourceBreakdown(@Param("plantCode") String plantCode);

    /**
     * 多维度分析（按 defectDesc / supplier / material / time 聚合）。
     */
    @InterceptorIgnore(tenantLine = "true")
    List<ExceptionAnalysisItemVO> selectAnalysis(@Param("plantCode") String plantCode,
                                                   @Param("dimension") String dimension);

    /**
     * 重复问题检查（找 90 天内同类不良≥N 次的供应商）。
     */
    @InterceptorIgnore(tenantLine = "true")
    List<TriggeredSupplierVO> selectRepeatExceptions(@Param("plantCode") String plantCode,
                                                      @Param("daysWindow") int daysWindow,
                                                      @Param("minRepeatCount") int minRepeatCount);

    /**
     * 供应商来料不良频次汇总。
     */
    @InterceptorIgnore(tenantLine = "true")
    List<SupplierExceptionSummaryVO> selectSupplierSummary(@Param("plantCode") String plantCode,
                                                            @Param("supplierId") Long supplierId,
                                                            @Param("startDate") String startDate,
                                                            @Param("endDate") String endDate,
                                                            @Param("minCount") Integer minCount);

    /**
     * 全局查询指定前缀下的最大异常单号（跨厂区，绕过 tenant 拦截器）。
     * <p>异常单号 EX-YYYYMMDD-NNN 为全局唯一（uq_exo_no 不含 plant_code），
     * 编号生成必须跨厂区取最大序号，避免按厂过滤后与其他厂区已有编号冲突。</p>
     */
    @InterceptorIgnore(tenantLine = "true")
    String selectMaxExceptionNo(@Param("prefix") String prefix);
}
