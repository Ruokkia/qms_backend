package com.kangli.qms.api.supplier;

import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.R;
import com.kangli.qms.domain.supplier.vo.SupplierQualificationExpiryVO;
import com.kangli.qms.domain.supplier.vo.SupplierQualificationVO;
import com.kangli.qms.service.supplier.SupplierQualificationService;
import com.kangli.qms.service.supplier.dto.SupplierQualificationCreateDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * 供应商资质证照管理接口（挂在供应商模块 supplier 下，复用其权限配置）。
 */
@RestController
@RequestMapping("/api/v1/suppliers/qualifications")
@Api(tags = "供应商资质证照管理")
public class SupplierQualificationController {

    @Resource
    private SupplierQualificationService qualificationService;

    @PostMapping
    @ApiOperation("新增资质证照")
    public R<SupplierQualificationVO> create(@Valid @RequestBody SupplierQualificationCreateDTO dto) {
        return R.ok(qualificationService.create(dto));
    }

    @PutMapping("/{id}")
    @ApiOperation("更新资质证照")
    public R<SupplierQualificationVO> update(
            @ApiParam("资质ID") @PathVariable Long id,
            @Valid @RequestBody SupplierQualificationCreateDTO dto) {
        return R.ok(qualificationService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除资质证照（逻辑删除）")
    public R<Void> remove(@ApiParam("资质ID") @PathVariable Long id) {
        qualificationService.remove(id);
        return R.ok();
    }

    @GetMapping("/by-supplier")
    @ApiOperation("按供应商查询资质列表")
    public R<List<SupplierQualificationVO>> listBySupplier(
            @ApiParam(value = "供应商ID", required = true) @RequestParam Long supplierId) {
        return R.ok(qualificationService.listBySupplier(supplierId));
    }

    @GetMapping("/page")
    @ApiOperation("资质分页列表（支持预警级别过滤）")
    public R<PageResult<SupplierQualificationVO>> listPage(
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页条数") @RequestParam(defaultValue = "10") int size,
            @ApiParam("供应商ID") @RequestParam(required = false) Long supplierId,
            @ApiParam("预警级别：提醒/预警/紧急/已过期/正常") @RequestParam(required = false) String warnLevel,
            @ApiParam("关键词（资质类型/编号/供应商名）") @RequestParam(required = false) String keyword) {
        return R.ok(qualificationService.listPage(page, size, supplierId, warnLevel, keyword));
    }

    @GetMapping("/expiring")
    @ApiOperation("扫描即将到期/已过期资质（供看板与定时预警）")
    public R<List<SupplierQualificationExpiryVO>> scanExpiring() {
        return R.ok(qualificationService.scanExpiring());
    }
}
