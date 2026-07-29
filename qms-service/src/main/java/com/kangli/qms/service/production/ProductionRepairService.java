package com.kangli.qms.service.production;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.service.production.dto.ProductionRepairSaveDTO;
import com.kangli.qms.service.production.dto.ProductionRepairUpdateDTO;
import com.kangli.qms.domain.production.entity.ProductionRepair;
import com.kangli.qms.domain.production.vo.ProductionRepairImportResultVO;
import com.kangli.qms.domain.production.vo.ProductionRepairVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 生产维修记录 Service（DTO/VO 进出，含结构化校验与 Excel 导入）。
 */
public interface ProductionRepairService extends IService<ProductionRepair> {

    PageResult<ProductionRepairVO> pageQuery(int page, int size, String keyword, String process,
                                            String startDate, String endDate, String repairStatus,
                                            String defectPhenomenon, String productName,
                                            String productBatchOrSn, String productNo, LoginUser user);

    ProductionRepairVO detail(Long id, LoginUser user);

    Long create(ProductionRepairSaveDTO dto, LoginUser user);

    void update(Long id, ProductionRepairUpdateDTO dto, LoginUser user);

    void remove(Long id, LoginUser user);

    ProductionRepairImportResultVO importExcel(MultipartFile file, LoginUser user);
}
