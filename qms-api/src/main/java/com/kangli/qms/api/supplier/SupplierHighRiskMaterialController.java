package com.kangli.qms.api.supplier;

import com.kangli.qms.common.R;
import com.kangli.qms.domain.supplier.vo.HighRiskMaterialVO;
import com.kangli.qms.domain.supplier.vo.SupplierHighRiskMaterialVO;
import com.kangli.qms.service.supplier.SupplierHighRiskMaterialService;
import com.kangli.qms.service.supplier.dto.SupplierHighRiskMaterialCreateDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * 供应商高风险物料关联接口（挂在 supplier 模块下）。
 * <p>需求 1.1.1 高风险物料额外审核频次及资料要求。</p>
 */
@RestController
@RequestMapping("/api/v1/suppliers/high-risk-materials")
@Api(tags = "供应商高风险物料及额外审核要求")
public class SupplierHighRiskMaterialController {

    @Resource
    private SupplierHighRiskMaterialService service;

    @GetMapping("/materials")
    @ApiOperation("高风险物料清单（固化）")
    public R<List<HighRiskMaterialVO>> listMaterials() {
        return R.ok(service.listMaterials());
    }

    @GetMapping("/by-supplier")
    @ApiOperation("某供应商关联的高风险物料及额外要求")
    public R<List<SupplierHighRiskMaterialVO>> listBySupplier(
            @ApiParam(value = "供应商ID", required = true) @RequestParam Long supplierId) {
        return R.ok(service.listBySupplier(supplierId));
    }

    @PostMapping("/link")
    @ApiOperation("关联高风险物料到供应商")
    public R<SupplierHighRiskMaterialVO> add(@Valid @RequestBody SupplierHighRiskMaterialCreateDTO dto) {
        return R.ok(service.add(dto));
    }

    @DeleteMapping("/link/{id}")
    @ApiOperation("取消关联")
    public R<Void> remove(@ApiParam("关联ID") @PathVariable Long id) {
        service.remove(id);
        return R.ok();
    }
}
