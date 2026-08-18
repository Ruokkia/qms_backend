package com.kangli.qms.service.supplier.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.domain.supplier.entity.Supplier;
import com.kangli.qms.domain.supplier.entity.SupplierAuditFinding;
import com.kangli.qms.domain.supplier.entity.SupplierAuditPlan;
import com.kangli.qms.domain.supplier.entity.SupplierAuditRecord;
import com.kangli.qms.domain.supplier.entity.SupplierAuditRectification;
import com.kangli.qms.domain.supplier.mapper.SupplierAuditFindingMapper;
import com.kangli.qms.domain.supplier.mapper.SupplierAuditPlanMapper;
import com.kangli.qms.domain.supplier.mapper.SupplierAuditRecordMapper;
import com.kangli.qms.domain.supplier.mapper.SupplierAuditRectificationMapper;
import com.kangli.qms.domain.supplier.vo.SupplierAuditReportVO;
import com.kangli.qms.domain.supplier.vo.SupplierAuditReportVO.FindingWithRectification;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.service.notification.enums.NotificationTypeEnum;
import com.kangli.qms.domain.admin.vo.AdminUserVO;
import com.kangli.qms.domain.supplier.vo.SupplierAuditAuditorVO;
import com.kangli.qms.service.admin.AdminService;
import com.kangli.qms.service.supplier.SupplierAuditService;
import com.kangli.qms.service.supplier.SupplierHighRiskMaterialService;
import com.kangli.qms.service.supplier.SupplierService;
import com.kangli.qms.service.supplier.dto.SupplierAuditPlanCreateDTO;
import com.kangli.qms.service.supplier.dto.SupplierAuditRecordCreateDTO;
import com.kangli.qms.service.supplier.dto.SupplierAuditRecordCreateDTO.FindingItem;
import com.kangli.qms.service.supplier.dto.SupplierAuditRectifyDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 供应商现场审核业务实现。
 */
@Slf4j
@Service
public class SupplierAuditServiceImpl implements SupplierAuditService {

    private final SupplierAuditPlanMapper planMapper;
    private final SupplierAuditRecordMapper recordMapper;
    private final SupplierAuditFindingMapper findingMapper;
    private final SupplierAuditRectificationMapper rectificationMapper;
    private final SupplierService supplierService;
    private final NotificationService notificationService;
    private final AdminService adminService;
    private final SupplierHighRiskMaterialService highRiskMaterialService;

    public SupplierAuditServiceImpl(
            SupplierAuditPlanMapper planMapper,
            SupplierAuditRecordMapper recordMapper,
            SupplierAuditFindingMapper findingMapper,
            SupplierAuditRectificationMapper rectificationMapper,
            SupplierService supplierService,
            NotificationService notificationService,
            AdminService adminService,
            SupplierHighRiskMaterialService highRiskMaterialService) {
        this.planMapper = planMapper;
        this.recordMapper = recordMapper;
        this.findingMapper = findingMapper;
        this.rectificationMapper = rectificationMapper;
        this.supplierService = supplierService;
        this.notificationService = notificationService;
        this.adminService = adminService;
        this.highRiskMaterialService = highRiskMaterialService;
    }

    private LoginUser currentUser() {
        LoginUser u = LoginUserHolder.get();
        if (u == null || u.getPlantCode() == null) {
            throw new RuntimeException("未获取到登录用户信息");
        }
        return u;
    }

    private String plantCode() {
        return currentUser().getPlantCode().name();
    }

    private String plantName() {
        return currentUser().getPlantCode().getChineseName();
    }

    /** 风险等级 → 审核频次 */
    private String frequencyOf(String riskLevel) {
        if ("高".equals(riskLevel)) return "一年2次";
        if ("中".equals(riskLevel)) return "一年1次";
        return "两年1次";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SupplierAuditPlan> createPlan(SupplierAuditPlanCreateDTO dto) {
        LoginUser u = currentUser();
        List<SupplierAuditPlan> created = new ArrayList<>();
        String plantCode = plantCode();
        String plantName = plantName();

        if ("年度".equals(dto.getAuditType())) {
            if (dto.getPlanYear() == null) {
                throw new RuntimeException("年度计划必须指定年份");
            }
            // 遍历当前分公司全部供应商，按风险等级生成计划
            List<Supplier> suppliers = supplierService.lambdaQuery()
                    .eq(Supplier::getPlantCode, plantCode)
                    .list();
            for (Supplier s : suppliers) {
                String freq = frequencyOf(s.getRiskLevel());
                boolean hasHighRisk = highRiskMaterialService.getExtraRequirement(s.getId()) != null;
                SupplierAuditPlan plan = new SupplierAuditPlan();
                plan.setSupplierId(s.getId());
                plan.setSupplierCode(s.getSupplierCode());
                plan.setSupplierName(s.getSupplierName());
                plan.setAuditType("年度");
                plan.setPlanYear(dto.getPlanYear());
                plan.setFrequency(freq);
                plan.setStatus("草稿");
                plan.setAuditor(dto.getAuditor());
                plan.setRemark(dto.getRemark());
                plan.setPlantCode(plantCode);
                plan.setPlantName(plantName);
                plan.setCreatedBy(u.getRealName());
                plan.setUpdatedBy(u.getRealName());
                planMapper.insert(plan);
                created.add(plan);
                // 高风险物料供应商：额外增加一次专项审核（更高频次/资料要求）
                if (hasHighRisk) {
                    SupplierAuditPlan extra = new SupplierAuditPlan();
                    extra.setSupplierId(s.getId());
                    extra.setSupplierCode(s.getSupplierCode());
                    extra.setSupplierName(s.getSupplierName());
                    extra.setAuditType("专项");
                    extra.setPlanYear(dto.getPlanYear());
                    extra.setFrequency("高风险物料专项加严");
                    extra.setStatus("草稿");
                    extra.setAuditor(dto.getAuditor());
                    extra.setRemark("高风险物料额外审核：需提交额外资料要求（" + highRiskMaterialService.getExtraRequirement(s.getId()) + "）");
                    extra.setPlantCode(plantCode);
                    extra.setPlantName(plantName);
                    extra.setCreatedBy(u.getRealName());
                    extra.setUpdatedBy(u.getRealName());
                    planMapper.insert(extra);
                    created.add(extra);
                }
            }
            if (created.isEmpty()) {
                throw new RuntimeException("当前分公司无供应商，无法生成年度计划");
            }
            return created;
        }

        // 专项 / 临时：必须指定供应商与计划日期
        if (dto.getSupplierId() == null || !StringUtils.hasText(dto.getPlannedDate())) {
            throw new RuntimeException("专项/临时计划必须指定供应商与计划日期");
        }
        Supplier s = supplierService.getById(dto.getSupplierId());
        if (s == null) {
            throw new RuntimeException("供应商不存在");
        }
        SupplierAuditPlan plan = new SupplierAuditPlan();
        plan.setSupplierId(s.getId());
        plan.setSupplierCode(s.getSupplierCode());
        plan.setSupplierName(s.getSupplierName());
        plan.setAuditType(dto.getAuditType());
        plan.setFrequency(frequencyOf(s.getRiskLevel()));
        plan.setPlannedDate(LocalDate.parse(dto.getPlannedDate()));
        plan.setStatus("已排期");
        plan.setAuditor(dto.getAuditor());
        plan.setRemark(dto.getRemark());
        plan.setPlantCode(plantCode);
        plan.setPlantName(plantName);
        plan.setCreatedBy(u.getRealName());
        plan.setUpdatedBy(u.getRealName());
        planMapper.insert(plan);
        created.add(plan);
        return created;
    }

    @Override
    public PageResult<SupplierAuditPlan> listPlans(int page, int size, Long supplierId, String auditType, String status, String keyword) {
        LambdaQueryWrapper<SupplierAuditPlan> w = new LambdaQueryWrapper<>();
        w.eq(SupplierAuditPlan::getPlantCode, plantCode());
        if (supplierId != null) {
            w.eq(SupplierAuditPlan::getSupplierId, supplierId);
        }
        if (StringUtils.hasText(auditType)) {
            w.eq(SupplierAuditPlan::getAuditType, auditType);
        }
        if (StringUtils.hasText(status)) {
            w.eq(SupplierAuditPlan::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toUpperCase();
            w.and(x -> x.like(SupplierAuditPlan::getSupplierName, kw)
                    .or().like(SupplierAuditPlan::getSupplierCode, kw));
        }
        w.orderByDesc(SupplierAuditPlan::getCreatedAt);
        Page<SupplierAuditPlan> p = planMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p);
    }

    @Override
    public PageResult<SupplierAuditRecord> listRecords(int page, int size, Long supplierId, String keyword) {
        LambdaQueryWrapper<SupplierAuditRecord> w = new LambdaQueryWrapper<>();
        w.eq(SupplierAuditRecord::getPlantCode, plantCode());
        if (supplierId != null) {
            w.eq(SupplierAuditRecord::getSupplierId, supplierId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toUpperCase();
            w.and(x -> x.like(SupplierAuditRecord::getSupplierName, kw)
                    .or().like(SupplierAuditRecord::getSupplierCode, kw)
                    .or().like(SupplierAuditRecord::getReportNo, kw));
        }
        w.orderByDesc(SupplierAuditRecord::getCreatedAt);
        Page<SupplierAuditRecord> p = recordMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p);
    }

    @Override
    public PageResult<SupplierAuditFinding> listFindings(int page, int size, Long supplierId, String status, String keyword) {
        LambdaQueryWrapper<SupplierAuditFinding> w = new LambdaQueryWrapper<>();
        w.eq(SupplierAuditFinding::getPlantCode, plantCode());
        if (supplierId != null) {
            w.eq(SupplierAuditFinding::getSupplierId, supplierId);
        }
        if (StringUtils.hasText(status)) {
            w.eq(SupplierAuditFinding::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toUpperCase();
            w.and(x -> x.like(SupplierAuditFinding::getSupplierCode, kw)
                    .or().like(SupplierAuditFinding::getDescription, kw));
        }
        w.orderByDesc(SupplierAuditFinding::getCreatedAt);
        Page<SupplierAuditFinding> p = findingMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierAuditRecord createRecord(SupplierAuditRecordCreateDTO dto) {
        LoginUser u = currentUser();
        Supplier s = supplierService.getById(dto.getSupplierId());
        if (s == null) {
            throw new RuntimeException("供应商不存在");
        }
        SupplierAuditRecord record = new SupplierAuditRecord();
        record.setPlanId(dto.getPlanId());
        record.setSupplierId(s.getId());
        record.setSupplierCode(s.getSupplierCode());
        record.setSupplierName(s.getSupplierName());
        record.setReportNo(generateReportNo());
        record.setAuditDate(LocalDate.parse(dto.getAuditDate()));
        record.setAuditor(dto.getAuditor());
        record.setAuditSummary(dto.getAuditSummary());
        // 高风险物料额外资料要求自动汇总
        record.setExtraRequirement(highRiskMaterialService.getExtraRequirement(s.getId()));
        record.setPlantCode(plantCode());
        record.setPlantName(plantName());
        record.setCreatedBy(u.getRealName());
        record.setUpdatedBy(u.getRealName());
        recordMapper.insert(record);

        if (dto.getFindings() != null) {
            for (FindingItem item : dto.getFindings()) {
                SupplierAuditFinding f = new SupplierAuditFinding();
                f.setRecordId(record.getId());
                f.setSupplierId(s.getId());
                f.setSupplierCode(s.getSupplierCode());
                f.setLevel(item.getLevel());
                f.setDescription(item.getDescription());
                if (item.getPhotoUrls() != null && !item.getPhotoUrls().isEmpty()) {
                    f.setPhotoUrls(String.join(",", item.getPhotoUrls()));
                }
                f.setStatus("待整改");
                f.setPlantCode(plantCode());
                f.setPlantName(plantName());
                f.setCreatedBy(u.getRealName());
                f.setUpdatedBy(u.getRealName());
                findingMapper.insert(f);
            }
        }

        // 关联计划置为已完成
        if (dto.getPlanId() != null) {
            SupplierAuditPlan plan = planMapper.selectById(dto.getPlanId());
            if (plan != null && !"已完成".equals(plan.getStatus())) {
                plan.setStatus("已完成");
                plan.setUpdatedBy(u.getRealName());
                planMapper.updateById(plan);
            }
        }
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rectify(Long findingId, SupplierAuditRectifyDTO dto) {
        LoginUser u = currentUser();
        SupplierAuditFinding finding = findingMapper.selectById(findingId);
        if (finding == null) {
            throw new RuntimeException("不符合项不存在");
        }
        SupplierAuditRectification rect = rectificationMapper.selectOne(
                new LambdaQueryWrapper<SupplierAuditRectification>().eq(SupplierAuditRectification::getFindingId, findingId));
        if (rect == null) {
            rect = new SupplierAuditRectification();
            rect.setFindingId(findingId);
            rect.setPlantCode(finding.getPlantCode());
            rect.setPlantName(finding.getPlantName());
        }
        rect.setMeasure(dto.getMeasure());
        if (StringUtils.hasText(dto.getDueDate())) {
            rect.setDueDate(LocalDate.parse(dto.getDueDate()));
        }
        rect.setOwner(dto.getOwner());
        rect.setCreatedBy(u.getRealName());
        rect.setUpdatedBy(u.getRealName());
        if (rect.getId() == null) {
            rectificationMapper.insert(rect);
        } else {
            rectificationMapper.updateById(rect);
        }
        finding.setStatus("待验证");
        finding.setUpdatedBy(u.getRealName());
        findingMapper.updateById(finding);

        pushRectifyNotification(finding, "不符合项已提交整改措施，请跟进验证。");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long findingId, SupplierAuditRectifyDTO dto) {
        LoginUser u = currentUser();
        SupplierAuditFinding finding = findingMapper.selectById(findingId);
        if (finding == null) {
            throw new RuntimeException("不符合项不存在");
        }
        SupplierAuditRectification rect = rectificationMapper.selectOne(
                new LambdaQueryWrapper<SupplierAuditRectification>().eq(SupplierAuditRectification::getFindingId, findingId));
        if (rect == null) {
            rect = new SupplierAuditRectification();
            rect.setFindingId(findingId);
            rect.setPlantCode(finding.getPlantCode());
            rect.setPlantName(finding.getPlantName());
        }
        rect.setVerifyResult(dto.getVerifyResult());
        rect.setVerifiedBy(StringUtils.hasText(dto.getVerifiedBy()) ? dto.getVerifiedBy() : u.getRealName());
        rect.setUpdatedBy(u.getRealName());
        if (rect.getId() == null) {
            rectificationMapper.insert(rect);
        } else {
            rectificationMapper.updateById(rect);
        }
        if (dto.getVerifyResult() != null && dto.getVerifyResult().startsWith("通过")) {
            finding.setStatus("已闭环");
            finding.setUpdatedBy(u.getRealName());
            findingMapper.updateById(finding);
            rect.setClosedAt(LocalDateTime.now());
            rectificationMapper.updateById(rect);
            pushRectifyNotification(finding, "不符合项整改已验证通过并闭环。");
        } else {
            finding.setStatus("整改中");
            finding.setUpdatedBy(u.getRealName());
            findingMapper.updateById(finding);
            pushRectifyNotification(finding, "不符合项整改验证未通过，请重新整改。");
        }
    }

    @Override
    public SupplierAuditReportVO getReport(Long recordId) {
        SupplierAuditRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new RuntimeException("审核记录不存在");
        }
        List<SupplierAuditFinding> findings = findingMapper.selectList(
                new LambdaQueryWrapper<SupplierAuditFinding>().eq(SupplierAuditFinding::getRecordId, recordId));
        List<FindingWithRectification> list = findings.stream().map(f -> {
            FindingWithRectification fr = new FindingWithRectification();
            fr.setFinding(f);
            SupplierAuditRectification rect = rectificationMapper.selectOne(
                    new LambdaQueryWrapper<SupplierAuditRectification>().eq(SupplierAuditRectification::getFindingId, f.getId()));
            if (rect != null) {
                fr.setMeasure(rect.getMeasure());
                fr.setOwner(rect.getOwner());
                fr.setVerifyResult(rect.getVerifyResult());
            }
            fr.setClosed("已闭环".equals(f.getStatus()));
            return fr;
        }).collect(Collectors.toList());

        SupplierAuditReportVO vo = new SupplierAuditReportVO();
        vo.setRecord(record);
        vo.setFindings(list);
        return vo;
    }

    @Override
    public List<SupplierAuditAuditorVO> listAuditors() {
        LoginUser u = currentUser();
        String plant = u.getPlantCode().name();
        List<AdminUserVO> users = adminService.listUsers();
        if (users == null) {
            return java.util.Collections.emptyList();
        }
        return users.stream()
                .filter(x -> x.getStatus() != null && x.getStatus() == 1)
                .filter(x -> plant.equals(x.getPlantCode()))
                .map(x -> {
                    SupplierAuditAuditorVO vo = new SupplierAuditAuditorVO();
                    vo.setId(x.getId());
                    vo.setAccount(x.getAccount());
                    vo.setRealName(x.getRealName());
                    vo.setRoleCode(x.getRoleCode());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private String generateReportNo() {
        return "SA-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + System.nanoTime() % 10000;
    }

    private void pushRectifyNotification(SupplierAuditFinding finding, String content) {
        try {
            NotificationCreateDTO n = new NotificationCreateDTO();
            n.setUserId(currentUser().getUserId());
            n.setType(NotificationTypeEnum.ACTION_OWNER_ASSIGNED.getCode());
            n.setTitle("供应商审核整改通知");
            n.setContent("供应商[" + finding.getSupplierCode() + "]" + content);
            n.setLevel("提醒");
            n.setBusinessType("SUPPLIER_AUDIT");
            n.setBusinessId(finding.getId());
            n.setPlantCode(finding.getPlantCode());
            n.setCreatedBy(currentUser().getRealName());
            notificationService.createNotification(n);
        } catch (Exception e) {
            log.warn("推送供应商审核整改通知失败：findingId={}", finding.getId(), e);
        }
    }
}
