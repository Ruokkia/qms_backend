package com.kangli.qms.service.supplier;

import com.kangli.qms.domain.supplier.vo.HighRiskMaterialVO;
import com.kangli.qms.domain.supplier.vo.SupplierHighRiskMaterialVO;
import com.kangli.qms.service.supplier.dto.SupplierHighRiskMaterialCreateDTO;

import java.util.List;

/**
 * 供应商高风险物料关联业务接口。
 */
public interface SupplierHighRiskMaterialService {

    /** 高风险物料清单（固化数据，全量） */
    List<HighRiskMaterialVO> listMaterials();

    /** 某供应商关联的高风险物料及额外要求 */
    List<SupplierHighRiskMaterialVO> listBySupplier(Long supplierId);

    /** 关联高风险物料到供应商 */
    SupplierHighRiskMaterialVO add(SupplierHighRiskMaterialCreateDTO dto);

    /** 取消关联 */
    void remove(Long id);

    /** 聚合某供应商涉及的额外资料要求（换行分隔），无则 null */
    String getExtraRequirement(Long supplierId);
}
