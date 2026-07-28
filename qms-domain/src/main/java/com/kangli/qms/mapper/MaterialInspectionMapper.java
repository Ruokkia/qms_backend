package com.kangli.qms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kangli.qms.entity.MaterialInspection;
import com.kangli.qms.vo.DailyTrendItemVO;
import com.kangli.qms.vo.DefectDescItemVO;
import com.kangli.qms.vo.KeySupplierTrendRowVO;
import com.kangli.qms.vo.MaterialInspectionStatsVO;
import com.kangli.qms.vo.SupplierRankItemVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/**
 * 物料检验 Mapper — 含看板统计 + 对账查询的自定义查询。
 */
public interface MaterialInspectionMapper extends BaseMapper<MaterialInspection> {

    /** 看板基本统计 */
    MaterialInspectionStatsVO selectStatsBase(@Param("plantCode") String plantCode);

    /** 高频不合格描述 TOP10 */
    List<DefectDescItemVO> selectTopDefectDesc(@Param("plantCode") String plantCode);

    /** 供应商合格率排名 */
    List<SupplierRankItemVO> selectSupplierRank(@Param("plantCode") String plantCode);

    /** 日统计趋势 */
    List<DailyTrendItemVO> selectDailyTrend(@Param("plantCode") String plantCode);

    /** 重点供应商 Top5（近30天来料批次量最大） */
    List<SupplierRankItemVO> selectKeySuppliers(@Param("plantCode") String plantCode);

    /** 重点供应商近30天每日合格率矩阵（与日期序列外连接，无来料当天为 null） */
    List<KeySupplierTrendRowVO> selectKeySupplierTrend(@Param("plantCode") String plantCode);

    /** 查询未关联异常单的不合格来料检验记录 */
    List<MaterialInspection> selectUnlinkedUnqualified(@Param("plantCode") String plantCode,
                                                         @Param("inspectionResult") String inspectionResult,
                                                         @Param("sourceType") String sourceType,
                                                         @Param("startAt") LocalDateTime startAt,
                                                         @Param("endAt") LocalDateTime endAt);

    /** 同分公司、供应商、物料在指定日期窗口内的不合格批次数 */
    int countUnqualifiedBatches(@Param("plantCode") String plantCode,
                                @Param("supplierCode") String supplierCode,
                                @Param("materialCode") String materialCode,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);
}

