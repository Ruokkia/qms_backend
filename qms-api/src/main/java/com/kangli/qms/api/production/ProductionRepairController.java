package com.kangli.qms.api.production;

import com.kangli.qms.common.*;
import com.kangli.qms.service.production.dto.ProductionRepairSaveDTO;
import com.kangli.qms.service.production.dto.ProductionRepairUpdateDTO;
import com.kangli.qms.service.production.ProductionRepairService;
import com.kangli.qms.domain.production.vo.ProductionRepairImportResultVO;
import com.kangli.qms.domain.production.vo.ProductionRepairVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

/**
 * M1 生产维修记录 Controller（DTO/VO 进出，结构化校验，分公司隔离）。
 * <p>路径：/api/v1/production-repairs</p>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/production-repairs")
@Api(tags = "M1-生产维修记录")
public class ProductionRepairController {

    private final ProductionRepairService productionRepairService;

    public ProductionRepairController(ProductionRepairService productionRepairService) {
        this.productionRepairService = productionRepairService;
    }

    @GetMapping
    @ApiOperation(value = "分页查询生产维修记录（支持多维度筛选）")
    public R<PageResult<ProductionRepairVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String process,
            @RequestParam(required = false) String defectPhenomenon,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productBatchOrSn,
            @RequestParam(required = false) String productNo,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String repairStatus) {
        LoginUser loginUser = currentUser();
        return R.ok(productionRepairService.pageQuery(page, size, keyword, process, startDate, endDate, repairStatus,
                defectPhenomenon, productName, productBatchOrSn, productNo, loginUser));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "生产维修记录详情")
    public R<ProductionRepairVO> detail(@ApiParam(value = "记录ID") @PathVariable Long id) {
        return R.ok(productionRepairService.detail(id, currentUser()));
    }

    @PostMapping
    @ApiOperation(value = "新增生产维修记录（结构化采集）")
    public R<ProductionRepairVO> create(@Valid @RequestBody ProductionRepairSaveDTO dto) {
        LoginUser loginUser = currentUser();
        Long id = productionRepairService.create(dto, loginUser);
        return R.ok(productionRepairService.detail(id, loginUser), "新增成功");
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "更新生产维修记录")
    public R<ProductionRepairVO> update(@ApiParam(value = "记录ID") @PathVariable Long id,
                                       @Valid @RequestBody ProductionRepairUpdateDTO dto) {
        LoginUser loginUser = currentUser();
        productionRepairService.update(id, dto, loginUser);
        return R.ok(productionRepairService.detail(id, loginUser), "更新成功");
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "逻辑删除生产维修记录")
    public R<Void> delete(@ApiParam(value = "记录ID") @PathVariable Long id) {
        productionRepairService.remove(id, currentUser());
        return R.ok(null, "删除成功");
    }

    @PostMapping("/import")
    @ApiOperation(value = "生产维修 Excel 导入（仅质量工程师/质量经理 R04/R06）")
    public R<ProductionRepairImportResultVO> importExcel(
            @ApiParam(value = "Excel 文件", required = true) @RequestParam("file") MultipartFile file) {
        LoginUser loginUser = currentUser();
        assertManager(loginUser);
        return R.ok(productionRepairService.importExcel(file, loginUser), "导入完成");
    }

    private static final java.util.Set<String> MANAGER_ROLES = java.util.Set.of("R00", "R04", "R06");

    private void assertManager(LoginUser user) {
        if (user.getRoleCode() == null || !MANAGER_ROLES.contains(user.getRoleCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限：仅系统管理员/质量工程师/质量经理可导入");
        }
    }

    private LoginUser currentUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
