package com.kangli.qms.api.supplier;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.domain.supplier.entity.SupplierAssessment;
import com.kangli.qms.domain.supplier.mapper.SupplierAssessmentMapper;
import com.kangli.qms.domain.supplier.entity.Supplier;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.domain.spc.entity.SpcCapability;
import com.kangli.qms.domain.spc.mapper.SpcCapabilityMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/v1/supplier-assessments")
@Api(tags = "供应商基础管理-绩效评价")
public class SupplierAssessmentController {
    private final SupplierAssessmentMapper mapper;
    private final SupplierMapper supplierMapper;
    private final MaterialInspectionMapper inspectionMapper;
    private final SpcCapabilityMapper capabilityMapper;
    public SupplierAssessmentController(SupplierAssessmentMapper mapper, SupplierMapper supplierMapper,
                                        MaterialInspectionMapper inspectionMapper, SpcCapabilityMapper capabilityMapper) {
        this.mapper = mapper; this.supplierMapper = supplierMapper; this.inspectionMapper = inspectionMapper; this.capabilityMapper = capabilityMapper;
    }

    @GetMapping
    @ApiOperation("分页查询供应商评价")
    public R<PageResult<SupplierAssessment>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String period) {
        LoginUser user = currentUser();
        LambdaQueryWrapper<SupplierAssessment> q = new LambdaQueryWrapper<SupplierAssessment>()
                .eq(SupplierAssessment::getPlantCode, user.getPlantCode().name())
                .eq(period != null && !period.trim().isEmpty(), SupplierAssessment::getAssessmentPeriod, period)
                .orderByDesc(SupplierAssessment::getAssessmentPeriod)
                .orderByDesc(SupplierAssessment::getUpdatedAt);
        return R.ok(PageResult.of(mapper.selectPage(new Page<>(page, size), q)));
    }

    @GetMapping("/spc-evidence")
    @ApiOperation("按供应商来料物料自动汇总关键物料SPC过程能力")
    public R<Map<String, Object>> spcEvidence(@RequestParam Long supplierId) {
        LoginUser user = currentUser();
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) throw new BusinessException(ResultCode.NOT_FOUND, "供应商不存在");
        List<MaterialInspection> inspections = inspectionMapper.selectList(new LambdaQueryWrapper<MaterialInspection>()
                .eq(MaterialInspection::getPlantCode, user.getPlantCode().name())
                .and(w -> w.eq(MaterialInspection::getSupplierCode, supplier.getSupplierCode()).or().eq(MaterialInspection::getSupplierName, supplier.getSupplierName())));
        Set<String> materialCodes = new HashSet<>();
        for (MaterialInspection inspection : inspections) if (inspection.getMaterialCode() != null && !inspection.getMaterialCode().trim().isEmpty()) materialCodes.add(inspection.getMaterialCode());
        Map<String, Object> result = new LinkedHashMap<>(); result.put("materialCount", materialCodes.size());
        if (materialCodes.isEmpty()) { result.put("assessment", "样本不足，不评价"); result.put("message", "未找到该供应商的来料物料数据"); return R.ok(result); }
        List<SpcCapability> capabilities = capabilityMapper.selectList(new LambdaQueryWrapper<SpcCapability>()
                .eq(SpcCapability::getPlantCode, user.getPlantCode().name()).eq(SpcCapability::getItemType, "MATERIAL")
                .in(SpcCapability::getItemCode, materialCodes).orderByDesc(SpcCapability::getCreatedAt));
        Map<String, SpcCapability> latest = new HashMap<>(); for (SpcCapability capability : capabilities) latest.putIfAbsent(capability.getItemCode(), capability);
        BigDecimal minCpk = null; for (SpcCapability capability : latest.values()) if (capability.getCpk() != null && (minCpk == null || capability.getCpk().compareTo(minCpk) < 0)) minCpk = capability.getCpk();
        String assessment = minCpk == null ? "样本不足，不评价" : minCpk.compareTo(BigDecimal.valueOf(1.33)) >= 0 ? "过程稳定，能力满足" : "存在波动，需跟踪";
        result.put("assessment", assessment); result.put("minCpk", minCpk); result.put("capabilityMaterialCount", latest.size()); result.put("message", minCpk == null ? "关键物料尚无可用 SPC 能力指数" : "已按该供应商来料物料自动汇总 SPC 数据");
        return R.ok(result);
    }

    @PostMapping
    @ApiOperation("保存供应商评价并生成分级建议")
    public R<SupplierAssessment> save(@RequestBody SupplierAssessment assessment) {
        LoginUser user = currentUser();
        assessment.setPlantCode(user.getPlantCode().name());
        assessment.setPlantName(user.getPlantCode().getChineseName());
        assessment.setCreatedBy(user.getRealName());
        assessment.setUpdatedBy(user.getRealName());
        applyGrade(assessment);
        mapper.insert(assessment);
        return R.ok(assessment, "评价已保存");
    }

    @PutMapping("/{id}")
    @ApiOperation("更新供应商评价并重新生成分级建议")
    public R<SupplierAssessment> update(@PathVariable Long id, @RequestBody SupplierAssessment assessment) {
        SupplierAssessment existing = mapper.selectById(id);
        if (existing == null) throw new BusinessException(ResultCode.NOT_FOUND, "供应商评价不存在");
        assessment.setId(id);
        assessment.setPlantCode(existing.getPlantCode());
        assessment.setPlantName(existing.getPlantName());
        assessment.setUpdatedBy(currentUser().getRealName());
        applyGrade(assessment);
        mapper.updateById(assessment);
        return R.ok(mapper.selectById(id), "评价已更新");
    }

    private void applyGrade(SupplierAssessment a) {
        BigDecimal qualified = value(a.getQualifiedRate());
        BigDecimal defectScore = BigDecimal.valueOf(100).subtract(value(a.getDefectRate()));
        BigDecimal score = qualified.add(defectScore).add(value(a.getRectificationRate()))
                .add(value(a.getDeliveryRate())).add(value(a.getComplianceRate()))
                .divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);
        String grade = score.compareTo(BigDecimal.valueOf(90)) >= 0 ? "A"
                : score.compareTo(BigDecimal.valueOf(80)) >= 0 ? "B"
                : score.compareTo(BigDecimal.valueOf(70)) >= 0 ? "C" : "D";
        a.setGrade(grade);
        if ("A".equals(grade)) { a.setShareSuggestion("优先分配"); a.setAuditFrequency("年度审核"); a.setAdmissionSuggestion("保持准入"); }
        else if ("B".equals(grade)) { a.setShareSuggestion("正常份额"); a.setAuditFrequency("年度审核"); a.setAdmissionSuggestion("保持准入"); }
        else if ("C".equals(grade)) { a.setShareSuggestion("控制份额"); a.setAuditFrequency("半年审核"); a.setAdmissionSuggestion("限期改善"); }
        else { a.setShareSuggestion("暂停新增份额"); a.setAuditFrequency("专项审核"); a.setAdmissionSuggestion("复评后决定"); }
    }
    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private LoginUser currentUser() {
        LoginUser user = LoginUserHolder.get();
        if (user == null || user.getPlantCode() == null) throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        return user;
    }
}
