package com.kangli.qms.api.supplier;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.R;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.supplier.entity.SupplierAuditFinding;
import com.kangli.qms.domain.supplier.entity.SupplierAuditPlan;
import com.kangli.qms.domain.supplier.entity.SupplierAuditRecord;
import com.kangli.qms.domain.supplier.vo.SupplierAuditAuditorVO;
import com.kangli.qms.domain.supplier.vo.SupplierAuditReportVO;
import com.kangli.qms.service.supplier.SupplierAuditService;
import com.kangli.qms.service.supplier.dto.SupplierAuditPlanCreateDTO;
import com.kangli.qms.service.supplier.dto.SupplierAuditRecordCreateDTO;
import com.kangli.qms.service.supplier.dto.SupplierAuditRectifyDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商现场审核管理 Controller。路径前缀 /api/v1/supplier-audits。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/supplier-audits")
@Api(tags = "M7-供应商现场审核")
public class SupplierAuditController {

    private final SupplierAuditService auditService;

    public SupplierAuditController(SupplierAuditService auditService) {
        this.auditService = auditService;
    }

    private LoginUser current() {
        LoginUser u = LoginUserHolder.get();
        if (u == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return u;
    }

    @PostMapping("/plans")
    @ApiOperation(value = "制定审核计划（年度自动按风险等级分配频次 / 专项 / 临时）")
    public R<List<SupplierAuditPlan>> createPlan(@RequestBody SupplierAuditPlanCreateDTO dto) {
        if (!StringUtils.hasText(dto.getAuditType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审核类型不能为空");
        }
        return R.ok(auditService.createPlan(dto));
    }

    @GetMapping("/plans")
    @ApiOperation(value = "审核计划分页列表")
    public R<PageResult<SupplierAuditPlan>> listPlans(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @ApiParam(value = "供应商ID（联动供应商档案）") @RequestParam(required = false) Long supplierId,
            @ApiParam(value = "审核类型过滤") @RequestParam(required = false) String auditType,
            @ApiParam(value = "状态过滤") @RequestParam(required = false) String status,
            @ApiParam(value = "关键词（供应商编号/名称）") @RequestParam(required = false) String keyword) {
        return R.ok(auditService.listPlans(page, size, supplierId, auditType, status, keyword));
    }

    @GetMapping("/records")
    @ApiOperation(value = "审核记录分页列表")
    public R<PageResult<SupplierAuditRecord>> listRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @ApiParam(value = "供应商ID过滤") @RequestParam(required = false) Long supplierId,
            @ApiParam(value = "关键词") @RequestParam(required = false) String keyword) {
        return R.ok(auditService.listRecords(page, size, supplierId, keyword));
    }

    @GetMapping("/findings")
    @ApiOperation(value = "不符合项分页列表（按状态过滤）")
    public R<PageResult<SupplierAuditFinding>> listFindings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @ApiParam(value = "供应商ID（联动供应商档案）") @RequestParam(required = false) Long supplierId,
            @ApiParam(value = "状态过滤：待整改/整改中/待验证/已闭环") @RequestParam(required = false) String status,
            @ApiParam(value = "关键词") @RequestParam(required = false) String keyword) {
        return R.ok(auditService.listFindings(page, size, supplierId, status, keyword));
    }

    @PostMapping("/records")
    @ApiOperation(value = "新建审核记录（含不符合项与照片URL）")
    public R<SupplierAuditRecord> createRecord(@RequestBody SupplierAuditRecordCreateDTO dto) {        if (dto.getSupplierId() == null || !StringUtils.hasText(dto.getAuditDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "供应商ID与审核日期不能为空");
        }
        return R.ok(auditService.createRecord(dto));
    }

    @PostMapping("/findings/{id}/rectify")
    @ApiOperation(value = "提交整改措施（状态 待整改→整改中）")
    public R<Void> rectify(
            @ApiParam(value = "不符合项ID") @PathVariable Long id,
            @RequestBody SupplierAuditRectifyDTO dto) {
        auditService.rectify(id, dto);
        return R.ok(null, "整改措施已提交");
    }

    @PostMapping("/findings/{id}/verify")
    @ApiOperation(value = "验证结果 + 闭环（通过→已闭环）")
    public R<Void> verify(
            @ApiParam(value = "不符合项ID") @PathVariable Long id,
            @RequestBody SupplierAuditRectifyDTO dto) {
        if (!StringUtils.hasText(dto.getVerifyResult())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "验证结果不能为空");
        }
        auditService.verify(id, dto);
        return R.ok(null, "验证完成");
    }

    @GetMapping("/records/{id}/report")
    @ApiOperation(value = "审核报告（聚合记录 + 不符合项 + 整改状态）")
    public R<SupplierAuditReportVO> getReport(
            @ApiParam(value = "审核记录ID") @PathVariable Long id) {
        return R.ok(auditService.getReport(id));
    }

    @GetMapping("/auditors")
    @ApiOperation(value = "审核人下拉选项（当前分公司启用用户）")
    public R<List<SupplierAuditAuditorVO>> listAuditors() {
        return R.ok(auditService.listAuditors());
    }
}
