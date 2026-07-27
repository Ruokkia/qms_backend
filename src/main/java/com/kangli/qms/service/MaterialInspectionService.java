package com.kangli.qms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kangli.qms.dto.MaterialInspectionImportDTO;
import com.kangli.qms.dto.MaterialInspectionImportResultVO;
import com.kangli.qms.dto.MaterialInspectionReconcileResultVO;
import com.kangli.qms.entity.MaterialInspection;
import com.kangli.qms.vo.KeySupplierTrendVO;
import com.kangli.qms.vo.MaterialInspectionStatsVO;

import java.util.List;

public interface MaterialInspectionService extends IService<MaterialInspection> {

    /** 来料检验看板统计 */
    MaterialInspectionStatsVO stats();

    /** 批量导入物料检验记录 */
    MaterialInspectionImportResultVO importRecords(MaterialInspectionImportDTO dto);

    /** 对账兜底（扫描未关联异常单的不合格记录并自动建单） */
    MaterialInspectionReconcileResultVO reconcile(String startDate, String endDate, String plantCode);

    /** 单条保存并触发字段驱动建异常单 */
    MaterialInspection saveWithException(MaterialInspection record, boolean autoCreateException);

    /** 查找未关联异常单的不合格记录 */
    List<MaterialInspection> findUnlinkedUnqualified(String startDate, String endDate, String plantCode);

    /** 重点供应商质量趋势（近30天来料批次量 Top5） */
    KeySupplierTrendVO keySupplierTrend();
}

