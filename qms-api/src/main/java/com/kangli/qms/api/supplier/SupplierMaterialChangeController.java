package com.kangli.qms.api.supplier;

import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.R;
import com.kangli.qms.service.supplier.SupplierMaterialChangeService;
import com.kangli.qms.service.supplier.dto.SupplierMaterialChangeApproveDTO;
import com.kangli.qms.service.supplier.dto.SupplierMaterialChangeCreateDTO;
import com.kangli.qms.service.supplier.dto.SupplierMaterialChangeDetailVO;
import com.kangli.qms.service.supplier.dto.SupplierMaterialChangeRejectDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 供应商物料变更管理 Controller。路径前缀 /api/v1/supplier-material-changes。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/supplier-material-changes")
@Api(tags = "供应商物料变更管理")
public class SupplierMaterialChangeController {

    private final SupplierMaterialChangeService changeService;

    public SupplierMaterialChangeController(SupplierMaterialChangeService changeService) {
        this.changeService = changeService;
    }

    @PostMapping
    @ApiOperation(value = "提交变更申请（创建主单 + 质量/采购/研发三条审批记录）")
    public R<SupplierMaterialChangeDetailVO> create(@RequestBody SupplierMaterialChangeCreateDTO dto) {
        return R.ok(changeService.create(dto));
    }

    @GetMapping
    @ApiOperation(value = "变更单分页列表")
    public R<PageResult<SupplierMaterialChangeDetailVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @ApiParam(value = "关键词（变更单号/供应商/物料）") @RequestParam(required = false) String keyword,
            @ApiParam(value = "状态：DRAFT/PENDING/APPROVED/REJECTED/VOID") @RequestParam(required = false) String status,
            @ApiParam(value = "变更类型：SPEC/PROCESS/ORIGIN") @RequestParam(required = false) String changeType) {
        return R.ok(changeService.page(page, size, keyword, status, changeType));
    }

    @GetMapping("/my-approvals")
    @ApiOperation(value = "我的审批列表（当前用户待审批的记录）")
    public R<PageResult<SupplierMaterialChangeDetailVO>> myApprovals(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(changeService.myApprovals(page, size));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "变更单详情（聚合审批记录）")
    public R<SupplierMaterialChangeDetailVO> detail(@ApiParam(value = "变更单ID") @PathVariable Long id) {
        return R.ok(changeService.detail(id));
    }

    @PostMapping("/{id}/approve")
    @ApiOperation(value = "审批通过（当前角色审批人操作）")
    public R<Void> approve(
            @ApiParam(value = "变更单ID") @PathVariable Long id,
            @RequestBody SupplierMaterialChangeApproveDTO dto) {
        changeService.approve(id, dto);
        return R.ok(null, "审批通过");
    }

    @PostMapping("/{id}/reject")
    @ApiOperation(value = "审批驳回（当前角色审批人操作，一票否决）")
    public R<Void> reject(
            @ApiParam(value = "变更单ID") @PathVariable Long id,
            @RequestBody SupplierMaterialChangeRejectDTO dto) {
        changeService.reject(id, dto);
        return R.ok(null, "已驳回");
    }

    @PostMapping("/{id}/void")
    @ApiOperation(value = "作废变更单（仅草稿/审批中）")
    public R<Void> voidChange(@ApiParam(value = "变更单ID") @PathVariable Long id) {
        changeService.voidChange(id);
        return R.ok(null, "已作废");
    }
}
