package com.kangli.qms.service.supplier;

import com.kangli.qms.common.PageResult;
import com.kangli.qms.domain.supplier.vo.SupplierQualificationExpiryVO;
import com.kangli.qms.domain.supplier.vo.SupplierQualificationVO;
import com.kangli.qms.service.supplier.dto.SupplierQualificationCreateDTO;

import java.util.List;

/**
 * 供应商资质证照业务接口。
 */
public interface SupplierQualificationService {

    /** 新增资质 */
    SupplierQualificationVO create(SupplierQualificationCreateDTO dto);

    /** 更新资质 */
    SupplierQualificationVO update(Long id, SupplierQualificationCreateDTO dto);

    /** 删除资质（逻辑删除） */
    void remove(Long id);

    /** 资质列表（按供应商ID，plantCode 隔离） */
    List<SupplierQualificationVO> listBySupplier(Long supplierId);

    /** 分页列表（支持预警级别过滤，plantCode 隔离） */
    PageResult<SupplierQualificationVO> listPage(int page, int size, Long supplierId, String warnLevel, String keyword);

    /** 扫描即将到期/已过期的资质（供定时预警使用） */
    List<SupplierQualificationExpiryVO> scanExpiring();
}
