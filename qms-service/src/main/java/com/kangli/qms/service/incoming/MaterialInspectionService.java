package com.kangli.qms.service.incoming;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kangli.qms.service.incoming.dto.MaterialInspectionImportDTO;
import com.kangli.qms.service.incoming.dto.MaterialInspectionImportPreviewVO;
import com.kangli.qms.service.incoming.dto.MaterialInspectionImportResultVO;
import com.kangli.qms.service.incoming.dto.MaterialInspectionReconcileResultVO;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.vo.KeySupplierTrendVO;
import com.kangli.qms.domain.incoming.vo.MaterialInspectionStatsVO;
import com.kangli.qms.domain.incoming.vo.SupplierRankItemVO;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MaterialInspectionService extends IService<MaterialInspection> {

    /** 来料检验看板统计 */
    MaterialInspectionStatsVO stats();

    /** 批量导入物料检验记录 */
    MaterialInspectionImportResultVO importRecords(MaterialInspectionImportDTO dto);

    /** 导入预览：解析 Excel 并逐行校验（不落库），返回可导入列表与失败明细 */
    MaterialInspectionImportPreviewVO previewImport(MultipartFile file);

    /** 生成导入 Excel 模板字节流 */
    byte[] generateTemplate();

    /** 对账兜底（扫描未关联异常单的不合格记录并自动建单） */
    MaterialInspectionReconcileResultVO reconcile(String startDate, String endDate, String plantCode);

    /** 单条保存并触发字段驱动建异常单 */
    MaterialInspection saveWithException(MaterialInspection record, boolean autoCreateException);

    /** 查找未关联异常单的不合格记录 */
    List<MaterialInspection> findUnlinkedUnqualified(String startDate, String endDate, String plantCode);

    /** 重点供应商质量趋势（自定义时间范围内来料批次量 TopN） */
    KeySupplierTrendVO keySupplierTrend(int topN, String startDate, String endDate);

    /** 供应商合格率排名（可选时间范围） */
    List<SupplierRankItemVO> supplierRank(String startDate, String endDate);
}

