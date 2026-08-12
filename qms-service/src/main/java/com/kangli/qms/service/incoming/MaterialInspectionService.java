package com.kangli.qms.service.incoming;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.vo.KeySupplierTrendVO;
import com.kangli.qms.domain.incoming.vo.MaterialInspectionStatsVO;
import com.kangli.qms.domain.incoming.vo.SupplierRankItemVO;

import java.util.List;

public interface MaterialInspectionService extends IService<MaterialInspection> {

    /** 来料检验看板统计 */
    MaterialInspectionStatsVO stats();

    /** 单条保存并触发字段驱动建异常单 */
    MaterialInspection saveWithException(MaterialInspection record, boolean autoCreateException);

    /** 按物料条码查询检验详情（多条取最新，查不到返回 null） */
    MaterialInspection getByBarcode(String barcode);

    /** 重点供应商质量趋势（自定义时间范围内来料批次量 TopN） */
    KeySupplierTrendVO keySupplierTrend(int topN, String startDate, String endDate);

    /** 供应商合格率排名（可选时间范围） */
    List<SupplierRankItemVO> supplierRank(String startDate, String endDate);
}

