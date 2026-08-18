package com.kangli.qms.service.supplier.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.admin.vo.AdminUserVO;
import com.kangli.qms.domain.supplier.entity.SupplierMaterialChange;
import com.kangli.qms.domain.supplier.entity.SupplierMaterialChangeApproval;
import com.kangli.qms.domain.supplier.mapper.SupplierMaterialChangeApprovalMapper;
import com.kangli.qms.domain.supplier.mapper.SupplierMaterialChangeMapper;
import com.kangli.qms.service.admin.AdminService;
import com.kangli.qms.service.fai.FaiChangeTriggerService;
import com.kangli.qms.service.fai.dto.CreateChangeTriggerRequest;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.service.notification.enums.NotificationTypeEnum;
import com.kangli.qms.service.supplier.SupplierMaterialChangeService;
import com.kangli.qms.service.supplier.dto.SupplierMaterialChangeApproveDTO;
import com.kangli.qms.service.supplier.dto.SupplierMaterialChangeCreateDTO;
import com.kangli.qms.service.supplier.dto.SupplierMaterialChangeDetailVO;
import com.kangli.qms.service.supplier.dto.SupplierMaterialChangeRejectDTO;
import com.kangli.qms.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 供应商物料变更管理业务实现。
 * <p>变更申请 → 质量/采购/研发并行会签（一票否决）→ 批准后触发首件加严检验 + 通知。</p>
 */
@Slf4j
@Service
public class SupplierMaterialChangeServiceImpl implements SupplierMaterialChangeService {

    // 主单状态
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_VOID = "VOID";

    // 审批状态
    private static final String APPROVAL_PENDING = "PENDING";
    private static final String APPROVAL_APPROVED = "APPROVED";
    private static final String APPROVAL_REJECTED = "REJECTED";
    private static final String APPROVAL_CANCELLED = "CANCELLED";

    // 审批角色
    private static final String ROLE_QUALITY = "QUALITY";
    private static final String ROLE_PURCHASE = "PURCHASE";
    private static final String ROLE_RD = "RD";

    private static final String BUSINESS_TYPE = "SUPPLIER_MATERIAL_CHANGE";

    private final SupplierMaterialChangeMapper changeMapper;
    private final SupplierMaterialChangeApprovalMapper approvalMapper;
    private final NotificationService notificationService;
    private final AdminService adminService;
    private final FaiChangeTriggerService faiChangeTriggerService;
    private final RedisUtil redisUtil;

    public SupplierMaterialChangeServiceImpl(
            SupplierMaterialChangeMapper changeMapper,
            SupplierMaterialChangeApprovalMapper approvalMapper,
            NotificationService notificationService,
            AdminService adminService,
            FaiChangeTriggerService faiChangeTriggerService,
            RedisUtil redisUtil) {
        this.changeMapper = changeMapper;
        this.approvalMapper = approvalMapper;
        this.notificationService = notificationService;
        this.adminService = adminService;
        this.faiChangeTriggerService = faiChangeTriggerService;
        this.redisUtil = redisUtil;
    }

    // ==================== 基础上下文 ====================

    private LoginUser currentUser() {
        LoginUser u = LoginUserHolder.get();
        if (u == null || u.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return u;
    }

    private String plantCode() {
        return currentUser().getPlantCode().name();
    }

    private String plantName() {
        return currentUser().getPlantCode().getChineseName();
    }

    // ==================== 提交变更申请 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierMaterialChangeDetailVO create(SupplierMaterialChangeCreateDTO dto) {
        LoginUser u = currentUser();
        validateChangeType(dto.getChangeType());

        // 校验三个审批人均存在且属于当前厂区
        List<AdminUserVO> users = listPlantUsers();
        AdminUserVO quality = requireApprover(users, dto.getQualityApproverId(), "质量审批人");
        AdminUserVO purchase = requireApprover(users, dto.getPurchaseApproverId(), "采购审批人");
        AdminUserVO rd = requireApprover(users, dto.getRdApproverId(), "研发审批人");

        SupplierMaterialChange change = new SupplierMaterialChange();
        change.setChangeNo(generateChangeNo(plantCode()));
        change.setApplicantId(u.getUserId());
        change.setApplicant(u.getRealName());
        change.setSupplierCode(dto.getSupplierCode());
        change.setSupplierName(dto.getSupplierName());
        change.setMaterialCode(dto.getMaterialCode());
        change.setMaterialName(dto.getMaterialName());
        change.setChangeType(dto.getChangeType());
        change.setChangeDesc(dto.getChangeDesc());
        change.setValidationReport(dto.getValidationReport());
        change.setRiskAssessment(dto.getRiskAssessment());
        change.setTightenedSubgroupSize(dto.getTightenedSubgroupSize());
        change.setSpcEnabled(Boolean.TRUE.equals(dto.getSpcEnabled()) ? (short) 1 : (short) 0);
        change.setStatus(STATUS_PENDING);
        change.setAttachments(dto.getAttachments());
        change.setPlantCode(plantCode());
        change.setPlantName(plantName());
        change.setCreatedBy(u.getRealName());
        change.setUpdatedBy(u.getRealName());
        changeMapper.insert(change);

        createApproval(change, ROLE_QUALITY, quality, u);
        createApproval(change, ROLE_PURCHASE, purchase, u);
        createApproval(change, ROLE_RD, rd, u);

        // 通知三个审批人
        String typeLabel = changeTypeLabel(change.getChangeType());
        notifyApprover(change, quality, "供应商物料变更待审批",
                "供应商[" + change.getSupplierName() + "] 物料[" + change.getMaterialName() + "] 变更申请（"
                        + typeLabel + "）待您审批，变更单号：" + change.getChangeNo());
        notifyApprover(change, purchase, "供应商物料变更待审批",
                "供应商[" + change.getSupplierName() + "] 物料[" + change.getMaterialName() + "] 变更申请（"
                        + typeLabel + "）待您审批，变更单号：" + change.getChangeNo());
        notifyApprover(change, rd, "供应商物料变更待审批",
                "供应商[" + change.getSupplierName() + "] 物料[" + change.getMaterialName() + "] 变更申请（"
                        + typeLabel + "）待您审批，变更单号：" + change.getChangeNo());

        log.info("[供应商物料变更] 提交申请 changeNo={} supplier={} material={} type={}",
                change.getChangeNo(), change.getSupplierCode(), change.getMaterialCode(), change.getChangeType());
        return buildDetail(change);
    }

    // ==================== 查询 ====================

    @Override
    public PageResult<SupplierMaterialChangeDetailVO> page(int page, int size, String keyword, String status, String changeType) {
        LambdaQueryWrapper<SupplierMaterialChange> w = new LambdaQueryWrapper<>();
        w.eq(SupplierMaterialChange::getPlantCode, plantCode());
        if (StringUtils.hasText(status)) {
            w.eq(SupplierMaterialChange::getStatus, status);
        }
        if (StringUtils.hasText(changeType)) {
            w.eq(SupplierMaterialChange::getChangeType, changeType);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toUpperCase();
            w.and(x -> x.like(SupplierMaterialChange::getChangeNo, kw)
                    .or().like(SupplierMaterialChange::getSupplierName, kw)
                    .or().like(SupplierMaterialChange::getSupplierCode, kw)
                    .or().like(SupplierMaterialChange::getMaterialName, kw)
                    .or().like(SupplierMaterialChange::getMaterialCode, kw));
        }
        w.orderByDesc(SupplierMaterialChange::getCreatedAt);
        Page<SupplierMaterialChange> p = changeMapper.selectPage(new Page<>(page, size), w);
        List<SupplierMaterialChangeDetailVO> list = p.getRecords().stream()
                .map(this::buildDetail)
                .collect(Collectors.toList());
        return new PageResult<>(list, p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public SupplierMaterialChangeDetailVO detail(Long id) {
        return buildDetail(requireChange(id));
    }

    @Override
    public PageResult<SupplierMaterialChangeDetailVO> myApprovals(int page, int size) {
        Long userId = currentUser().getUserId();
        Page<SupplierMaterialChangeApproval> ap = approvalMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SupplierMaterialChangeApproval>()
                        .eq(SupplierMaterialChangeApproval::getPlantCode, plantCode())
                        .eq(SupplierMaterialChangeApproval::getApproverId, userId)
                        .eq(SupplierMaterialChangeApproval::getApprovalStatus, APPROVAL_PENDING)
                        .orderByDesc(SupplierMaterialChangeApproval::getCreatedAt));
        List<SupplierMaterialChangeDetailVO> list = ap.getRecords().stream()
                .map(a -> {
                    SupplierMaterialChange change = changeMapper.selectById(a.getChangeId());
                    if (change == null) {
                        return null;
                    }
                    SupplierMaterialChangeDetailVO vo = buildDetail(change);
                    vo.setMyApprovalRole(a.getApprovalRole());
                    return vo;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new PageResult<>(list, ap.getTotal(), ap.getCurrent(), ap.getSize());
    }

    // ==================== 审批 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, SupplierMaterialChangeApproveDTO dto) {
        LoginUser u = currentUser();
        String role = validateRole(dto.getApprovalRole());
        SupplierMaterialChange change = requirePendingChange(id);
        SupplierMaterialChangeApproval approval = requireApproval(id, role);
        checkApprover(approval, u);
        if (!APPROVAL_PENDING.equals(approval.getApprovalStatus())) {
            throw new BusinessException(ResultCode.OPERATION_NOT_ALLOWED, "该角色已审批，请勿重复操作");
        }

        approval.setApprovalStatus(APPROVAL_APPROVED);
        approval.setOpinion(dto.getOpinion());
        approval.setApprover(u.getRealName());
        approval.setApproverId(u.getUserId());
        approval.setApprovedAt(LocalDateTime.now());
        approval.setUpdatedBy(u.getRealName());
        approvalMapper.updateById(approval);

        boolean allApproved = listApprovals(id).stream()
                .allMatch(a -> APPROVAL_APPROVED.equals(a.getApprovalStatus()));
        if (allApproved) {
            change.setStatus(STATUS_APPROVED);
            change.setUpdatedBy(u.getRealName());
            changeMapper.updateById(change);
            afterApproved(change);
            log.info("[供应商物料变更] 变更单已批准 changeNo={}", change.getChangeNo());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, SupplierMaterialChangeRejectDTO dto) {
        LoginUser u = currentUser();
        String role = validateRole(dto.getApprovalRole());
        SupplierMaterialChange change = requirePendingChange(id);
        SupplierMaterialChangeApproval approval = requireApproval(id, role);
        checkApprover(approval, u);
        if (!APPROVAL_PENDING.equals(approval.getApprovalStatus())) {
            throw new BusinessException(ResultCode.OPERATION_NOT_ALLOWED, "该角色已审批，请勿重复操作");
        }

        approval.setApprovalStatus(APPROVAL_REJECTED);
        approval.setOpinion(dto.getRejectReason());
        approval.setApprover(u.getRealName());
        approval.setApproverId(u.getUserId());
        approval.setApprovedAt(LocalDateTime.now());
        approval.setUpdatedBy(u.getRealName());
        approvalMapper.updateById(approval);

        // 一票否决：主单驳回，其余 PENDING 记录作废
        change.setStatus(STATUS_REJECTED);
        change.setRejectReason(dto.getRejectReason());
        change.setUpdatedBy(u.getRealName());
        changeMapper.updateById(change);

        for (SupplierMaterialChangeApproval a : listApprovals(id)) {
            if (APPROVAL_PENDING.equals(a.getApprovalStatus())) {
                a.setApprovalStatus(APPROVAL_CANCELLED);
                a.setUpdatedBy(u.getRealName());
                approvalMapper.updateById(a);
            }
        }

        notifyApplicant(change, "供应商物料变更申请已被驳回，原因：" + dto.getRejectReason(),
                NotificationTypeEnum.SUPPLIER_CHANGE_REJECTED);
        log.info("[供应商物料变更] 变更单被驳回 changeNo={} role={} reason={}",
                change.getChangeNo(), role, dto.getRejectReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidChange(Long id) {
        LoginUser u = currentUser();
        SupplierMaterialChange change = requireChange(id);
        if (!(STATUS_PENDING.equals(change.getStatus()) || STATUS_DRAFT.equals(change.getStatus()))) {
            throw new BusinessException(ResultCode.OPERATION_NOT_ALLOWED, "仅草稿/审批中的变更单可作废");
        }
        boolean isApplicant = u.getUserId().equals(change.getApplicantId());
        boolean isAdmin = "R06".equals(u.getRoleCode());
        if (!isApplicant && !isAdmin) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅申请人或管理员可作废");
        }

        change.setStatus(STATUS_VOID);
        change.setUpdatedBy(u.getRealName());
        changeMapper.updateById(change);

        for (SupplierMaterialChangeApproval a : listApprovals(id)) {
            if (APPROVAL_PENDING.equals(a.getApprovalStatus())) {
                a.setApprovalStatus(APPROVAL_CANCELLED);
                a.setUpdatedBy(u.getRealName());
                approvalMapper.updateById(a);
            }
        }
        log.info("[供应商物料变更] 变更单已作废 changeNo={}", change.getChangeNo());
    }

    // ==================== 批准后动作 ====================

    /**
     * 批准后动作：① 生成首件检验触发；② 通知人工维护物料检验标准 + 执行加严检验。
     * <p>在事务内执行，保证「批准 → 触发 → 通知」原子性；触发失败将回滚审批。</p>
     */
    private void afterApproved(SupplierMaterialChange change) {
        // ① 生成首件检验触发（加严检验：SPC 子组样本数 N）
        CreateChangeTriggerRequest req = new CreateChangeTriggerRequest();
        req.setTriggerType("供应商物料变更");
        req.setItemType("MATERIAL");
        req.setItemCode(change.getMaterialCode());
        req.setItemName(change.getMaterialName());
        req.setMaterialCode(change.getMaterialCode());
        req.setMaterialName(change.getMaterialName());
        req.setTriggerReason(buildTriggerReason(change));
        req.setRemark("供应商物料变更，变更单号：" + change.getChangeNo());
        faiChangeTriggerService.create(req, currentUser());

        // ② 通知申请人及相关部门：人工维护物料检验标准 + 按加严样本数执行首批加严检验
        Integer n = change.getTightenedSubgroupSize();
        String sampleInfo = (n != null && n > 0) ? "加严子组样本数 " + n : "按常规样本数";
        notifyApplicant(change,
                "您的变更申请（" + change.getChangeNo() + "）已全部审批通过。请到「检验标准维护」人工维护该物料检验标准；"
                        + "并按" + sampleInfo + "执行首批加严检验。",
                NotificationTypeEnum.SUPPLIER_CHANGE_APPROVED);
    }

    // ==================== 内部工具 ====================

    private SupplierMaterialChange requireChange(Long id) {
        SupplierMaterialChange change = changeMapper.selectById(id);
        if (change == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "变更单不存在");
        }
        if (!plantCode().equals(change.getPlantCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他厂区的变更单");
        }
        return change;
    }

    private SupplierMaterialChange requirePendingChange(Long id) {
        SupplierMaterialChange change = requireChange(id);
        if (!STATUS_PENDING.equals(change.getStatus())) {
            throw new BusinessException(ResultCode.OPERATION_NOT_ALLOWED, "仅审批中的变更单可审批");
        }
        return change;
    }

    private SupplierMaterialChangeApproval requireApproval(Long changeId, String role) {
        SupplierMaterialChangeApproval approval = approvalMapper.selectOne(
                new LambdaQueryWrapper<SupplierMaterialChangeApproval>()
                        .eq(SupplierMaterialChangeApproval::getChangeId, changeId)
                        .eq(SupplierMaterialChangeApproval::getApprovalRole, role));
        if (approval == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审批记录不存在");
        }
        return approval;
    }

    private List<SupplierMaterialChangeApproval> listApprovals(Long changeId) {
        return approvalMapper.selectList(new LambdaQueryWrapper<SupplierMaterialChangeApproval>()
                .eq(SupplierMaterialChangeApproval::getChangeId, changeId)
                .orderByAsc(SupplierMaterialChangeApproval::getId));
    }

    private void checkApprover(SupplierMaterialChangeApproval approval, LoginUser u) {
        boolean isAssigned = approval.getApproverId() != null && approval.getApproverId().equals(u.getUserId());
        boolean isAdmin = "R06".equals(u.getRoleCode());
        if (!isAssigned && !isAdmin) {
            throw new BusinessException(ResultCode.FORBIDDEN, "您不是该审批角色的审批人");
        }
    }

    private void createApproval(SupplierMaterialChange change, String role, AdminUserVO approver, LoginUser u) {
        SupplierMaterialChangeApproval approval = new SupplierMaterialChangeApproval();
        approval.setChangeId(change.getId());
        approval.setApprovalRole(role);
        approval.setApprover(approver.getRealName());
        approval.setApproverId(approver.getId());
        approval.setApprovalStatus(APPROVAL_PENDING);
        approval.setPlantCode(change.getPlantCode());
        approval.setPlantName(change.getPlantName());
        approval.setCreatedBy(u.getRealName());
        approval.setUpdatedBy(u.getRealName());
        approvalMapper.insert(approval);
    }

    private SupplierMaterialChangeDetailVO buildDetail(SupplierMaterialChange change) {
        SupplierMaterialChangeDetailVO vo = new SupplierMaterialChangeDetailVO();
        vo.setChange(change);
        List<SupplierMaterialChangeApproval> approvals = listApprovals(change.getId());
        vo.setApprovals(approvals);
        Long userId = currentUser().getUserId();
        vo.setMyApprovalRole(approvals.stream()
                .filter(a -> APPROVAL_PENDING.equals(a.getApprovalStatus()))
                .filter(a -> a.getApproverId() != null && a.getApproverId().equals(userId))
                .map(SupplierMaterialChangeApproval::getApprovalRole)
                .findFirst().orElse(null));
        return vo;
    }

    private String generateChangeNo(String plantCode) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq;
        try {
            seq = redisUtil.incrementWithTtl("smc:change_no:" + plantCode + ":" + date, 86400L);
        } catch (Exception e) {
            log.warn("[供应商物料变更] Redis 生成单号失败，回退时间戳", e);
            seq = System.nanoTime() % 10000;
        }
        return "SMC-" + plantCode + "-" + date + "-" + String.format("%04d", seq);
    }

    private List<AdminUserVO> listPlantUsers() {
        String plant = plantCode();
        List<AdminUserVO> users = adminService.listUsers();
        if (users == null) {
            return Collections.emptyList();
        }
        return users.stream()
                .filter(x -> x.getStatus() != null && x.getStatus() == 1)
                .filter(x -> plant.equals(x.getPlantCode()))
                .collect(Collectors.toList());
    }

    private AdminUserVO requireApprover(List<AdminUserVO> users, Long userId, String label) {
        if (userId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, label + "不能为空");
        }
        return users.stream()
                .filter(x -> x.getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, label + "不存在或不属于当前厂区"));
    }

    private void validateChangeType(String changeType) {
        if (!"SPEC".equals(changeType) && !"PROCESS".equals(changeType) && !"ORIGIN".equals(changeType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "变更类型不合法（应为 SPEC/PROCESS/ORIGIN）");
        }
    }

    private String validateRole(String role) {
        if (!ROLE_QUALITY.equals(role) && !ROLE_PURCHASE.equals(role) && !ROLE_RD.equals(role)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审批角色不合法（应为 QUALITY/PURCHASE/RD）");
        }
        return role;
    }

    private String changeTypeLabel(String changeType) {
        if ("SPEC".equals(changeType)) return "规格变更";
        if ("PROCESS".equals(changeType)) return "工艺变更";
        return "产地变更";
    }

    private String buildTriggerReason(SupplierMaterialChange change) {
        StringBuilder sb = new StringBuilder("供应商物料变更（").append(changeTypeLabel(change.getChangeType())).append("）");
        if (change.getTightenedSubgroupSize() != null && change.getTightenedSubgroupSize() > 0) {
            sb.append("，加严检验：SPC 子组样本数 ").append(change.getTightenedSubgroupSize());
        }
        return sb.toString();
    }

    private void notifyApprover(SupplierMaterialChange change, AdminUserVO approver, String title, String content) {
        try {
            NotificationCreateDTO n = new NotificationCreateDTO();
            n.setUserId(approver.getId());
            n.setType(NotificationTypeEnum.SUPPLIER_CHANGE_SUBMITTED.getCode());
            n.setTitle(title);
            n.setContent(content);
            n.setLevel("提醒");
            n.setBusinessType(BUSINESS_TYPE);
            n.setBusinessId(change.getId());
            n.setPlantCode(change.getPlantCode());
            n.setCreatedBy(currentUser().getRealName());
            notificationService.createNotification(n);
        } catch (Exception e) {
            log.warn("[供应商物料变更] 推送审批通知失败 changeNo={} approverId={}", change.getChangeNo(), approver.getId(), e);
        }
    }

    private void notifyApplicant(SupplierMaterialChange change, String content, NotificationTypeEnum type) {
        try {
            if (change.getApplicantId() == null) {
                return;
            }
            NotificationCreateDTO n = new NotificationCreateDTO();
            n.setUserId(change.getApplicantId());
            n.setType(type.getCode());
            n.setTitle("供应商物料变更通知");
            n.setContent(content);
            n.setLevel("重要");
            n.setBusinessType(BUSINESS_TYPE);
            n.setBusinessId(change.getId());
            n.setPlantCode(change.getPlantCode());
            n.setCreatedBy(currentUser().getRealName());
            notificationService.createNotification(n);
        } catch (Exception e) {
            log.warn("[供应商物料变更] 推送申请人通知失败 changeNo={}", change.getChangeNo(), e);
        }
    }
}
