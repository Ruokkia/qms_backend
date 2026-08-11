package com.kangli.qms.service.exception.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.PageResult;
import org.springframework.dao.DuplicateKeyException;
import java.time.LocalDateTime;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.exception.ExceptionConstants;
import com.kangli.qms.service.exception.ExceptionModuleHelper;
import com.kangli.qms.service.exception.dto.ExceptionCloseDTO;
import com.kangli.qms.service.exception.dto.ExceptionInitiateDTO;
import com.kangli.qms.service.exception.dto.ExceptionUpdateDTO;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.admin.vo.AdminUserVO;
import com.kangli.qms.domain.exception.entity.Exception8d;
import com.kangli.qms.domain.exception.entity.ExceptionApprovalConfig;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.entity.Escalation;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.exception.entity.ImprovementAction;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.exception.entity.RectificationPlan;
import com.kangli.qms.domain.supplier.entity.Supplier;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.exception.entity.VerificationRecord;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.domain.exception.mapper.EscalationMapper;
import com.kangli.qms.domain.exception.mapper.Exception8dMapper;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.mapper.ImprovementActionMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionRecordMapper;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.domain.exception.mapper.RectificationPlanMapper;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.exception.mapper.VerificationRecordMapper;
import com.kangli.qms.service.admin.AuditLogService;
import com.kangli.qms.service.admin.AdminService;
import com.kangli.qms.service.exception.ExceptionService;
import com.kangli.qms.service.exception.ExceptionApprovalConfigService;
import com.kangli.qms.service.notification.NotificationConfigService;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.service.notification.enums.NotificationTypeEnum;
import com.kangli.qms.service.notification.helper.NotificationTemplateHelper;
import com.kangli.qms.domain.exception.vo.CapaPhaseApprovalReadinessVO;
import com.kangli.qms.domain.exception.vo.CloseReadinessVO;
import com.kangli.qms.domain.exception.vo.EightDVO;
import com.kangli.qms.domain.exception.vo.ExceptionAnalysisItemVO;
import com.kangli.qms.domain.exception.vo.ExceptionAnalysisVO;
import com.kangli.qms.domain.exception.vo.ExceptionDetailVO;
import com.kangli.qms.domain.exception.vo.ExceptionStatsVO;
import com.kangli.qms.domain.supplier.vo.SupplierExceptionSummaryVO;
import com.kangli.qms.domain.exception.vo.QualityExceptionDecisionVO;
import com.kangli.qms.domain.exception.vo.QualityRuleCatalogVO;
import com.kangli.qms.domain.exception.vo.ExceptionUserOptionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * M2 异常与整改 Service 实现。
 * <p>核心业务：异常单 CRUD + 闭环前置条件校验 + KPI 统计 + 多维度分析 + 8D/通知关联 + 供应商频次。</p>
 */
@Slf4j
@Service
public class ExceptionServiceImpl implements ExceptionService {

    private static final String BUSINESS_TYPE_EXCEPTION = "EXCEPTION_ORDER";
    private static final String TABLE_NAME_EXCEPTION = "exception_order";
    private static final String ACTION_UPDATE = "UPDATE";

    @Lazy
    @Autowired
    private ExceptionServiceImpl self;

    private final ExceptionOrderMapper exceptionOrderMapper;
    private final ImprovementActionMapper improvementActionMapper;
    private final VerificationRecordMapper verificationRecordMapper;
    private final SupplierMapper supplierMapper;
    private final EscalationMapper escalationMapper;
    private final Exception8dMapper exception8dMapper;
    private final NotificationService notificationService;
    private final MaterialInspectionMapper materialInspectionMapper;
    private final FaiInspectionRecordMapper faiInspectionRecordMapper;
    private final FinishedGoodsInspectionMapper finishedGoodsInspectionMapper;
    private final SysUserMapper sysUserMapper;
    private final AuditLogMapper auditLogMapper;
    private final AuditLogService auditLogService;
    private final RectificationPlanMapper rectificationPlanMapper;
    private final QualityExceptionRuleEvaluator qualityRuleEvaluator;
    private final AdminService adminService;
    private final NotificationConfigService notificationConfigService;

    /** 阶段级审批配置服务（CAPA 审批门禁复用，与 8D 共享同一张配置表） */
    @Autowired
    private ExceptionApprovalConfigService approvalConfigService;

    public ExceptionServiceImpl(ExceptionOrderMapper exceptionOrderMapper,
                                 ImprovementActionMapper improvementActionMapper,
                                 VerificationRecordMapper verificationRecordMapper,
                                 SupplierMapper supplierMapper,
                                 EscalationMapper escalationMapper,
                                 Exception8dMapper exception8dMapper,
                                 NotificationService notificationService,
                                 MaterialInspectionMapper materialInspectionMapper,
                                 FaiInspectionRecordMapper faiInspectionRecordMapper,
                                 FinishedGoodsInspectionMapper finishedGoodsInspectionMapper,
                                 SysUserMapper sysUserMapper,
                                 AuditLogMapper auditLogMapper,
                                 AuditLogService auditLogService,
                                 RectificationPlanMapper rectificationPlanMapper,
                                 QualityExceptionRuleEvaluator qualityRuleEvaluator,
                                 AdminService adminService,
                                 NotificationConfigService notificationConfigService) {
        this.exceptionOrderMapper = exceptionOrderMapper;
        this.improvementActionMapper = improvementActionMapper;
        this.verificationRecordMapper = verificationRecordMapper;
        this.supplierMapper = supplierMapper;
        this.escalationMapper = escalationMapper;
        this.exception8dMapper = exception8dMapper;
        this.notificationService = notificationService;
        this.materialInspectionMapper = materialInspectionMapper;
        this.faiInspectionRecordMapper = faiInspectionRecordMapper;
        this.finishedGoodsInspectionMapper = finishedGoodsInspectionMapper;
        this.sysUserMapper = sysUserMapper;
        this.auditLogMapper = auditLogMapper;
        this.auditLogService = auditLogService;
        this.rectificationPlanMapper = rectificationPlanMapper;
        this.qualityRuleEvaluator = qualityRuleEvaluator;
        this.adminService = adminService;
        this.notificationConfigService = notificationConfigService;
    }

    // ===== 分页查询 =====

    @Override
    public PageResult<ExceptionOrder> list(int page, int size, String severity, String status,
                                            Long supplierId, String sourceType,
                                            String processType, String capaStatus,
                                            String startDate, String endDate) {
        String plantCode = ExceptionModuleHelper.currentPlantCodeSafe();
        Page<ExceptionOrder> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<ExceptionOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExceptionOrder::getPlantCode, plantCode);

        if (StringUtils.hasText(severity)) {
            wrapper.eq(ExceptionOrder::getSeverity, severity);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ExceptionOrder::getStatus, status);
        }
        if (supplierId != null) {
            wrapper.eq(ExceptionOrder::getSupplierId, supplierId);
        }
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(ExceptionOrder::getSourceType, sourceType);
        }
        if (StringUtils.hasText(processType)) {
            if ("NONE".equals(processType)) {
                // 前端"未选择"筛选 → process_type IS NULL
                wrapper.isNull(ExceptionOrder::getProcessType);
            } else {
                wrapper.eq(ExceptionOrder::getProcessType, processType);
            }
        }
        if (StringUtils.hasText(capaStatus)) {
            wrapper.eq(ExceptionOrder::getCapaStatus, capaStatus);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(ExceptionOrder::getCreatedAt, LocalDate.parse(startDate).atStartOfDay());
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(ExceptionOrder::getCreatedAt, LocalDate.parse(endDate).atTime(23, 59, 59));
        }

        wrapper.orderByDesc(ExceptionOrder::getCreatedAt);
        Page<ExceptionOrder> pageResult = exceptionOrderMapper.selectPage(pageObj, wrapper);
        fillSupplierNames(pageResult.getRecords());
        return PageResult.of(pageResult);
    }

    /** 批量回填异常单列表的供应商名称（避免 N+1），supplierId 为 null 时保留已有值 */
    private void fillSupplierNames(List<ExceptionOrder> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream()
                .map(ExceptionOrder::getSupplierId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            // supplierId 全部为 null，保留 entity 中已持久化的 supplierName
            return;
        }
        List<Supplier> suppliers = supplierMapper.selectBatchIds(ids);
        if (suppliers == null || suppliers.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = suppliers.stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getSupplierName, (a, b) -> a));
        records.forEach(r -> {
            if (r.getSupplierId() != null) {
                String lookedUp = nameMap.get(r.getSupplierId());
                if (lookedUp != null) {
                    r.setSupplierName(lookedUp);
                }
                // 查不到时保留 r.getSupplierName() 已有值（来自持久化字段）
            }
            // supplierId 为 null：保留 r.getSupplierName() 已有值
        });
    }

    // ===== 详情（含子数组 + 8D + 关联来料） =====

    @Override
    public ExceptionDetailVO detail(Long id) {
        ExceptionOrder order = exceptionOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + id);
        }

        ExceptionDetailVO vo = new ExceptionDetailVO();
        BeanUtils.copyProperties(order, vo);

        // 供应商名称
        if (order.getSupplierId() != null) {
            Supplier supplier = supplierMapper.selectById(order.getSupplierId());
            if (supplier != null) {
                vo.setSupplierName(supplier.getSupplierName());
            }
        }

        // 审核人姓名
        if (order.getReviewerId() != null) {
            SysUser user = sysUserMapper.selectById(order.getReviewerId());
            if (user != null) {
                vo.setReviewerName(user.getRealName());
            }
        }

        // 改善措施
        LambdaQueryWrapper<ImprovementAction> actionWrapper = new LambdaQueryWrapper<>();
        actionWrapper.eq(ImprovementAction::getExceptionId, id)
                .orderByAsc(ImprovementAction::getActionType);
        vo.setImprovementActions(improvementActionMapper.selectList(actionWrapper));

        // 验证记录
        LambdaQueryWrapper<VerificationRecord> verifyWrapper = new LambdaQueryWrapper<>();
        verifyWrapper.eq(VerificationRecord::getExceptionId, id)
                .orderByDesc(VerificationRecord::getVerifyDate);
        vo.setVerificationRecords(verificationRecordMapper.selectList(verifyWrapper));

        // 8D 报告
        Exception8d eightD = exception8dMapper.selectByExceptionId(id);
        if (eightD != null) {
            EightDVO eightDVO = new EightDVO();
            BeanUtils.copyProperties(eightD, eightDVO);
            eightDVO.setExceptionNo(order.getExceptionNo());
            vo.setEightD(eightDVO);
        }

        // 通知数
        LambdaQueryWrapper<com.kangli.qms.domain.notification.entity.Notification> notificationWrapper = new LambdaQueryWrapper<>();
        notificationWrapper.eq(com.kangli.qms.domain.notification.entity.Notification::getBusinessId, id)
                .eq(com.kangli.qms.domain.notification.entity.Notification::getBusinessType, BUSINESS_TYPE_EXCEPTION);
        vo.setNotificationCount((int) notificationService.count(notificationWrapper));

        // 关联来源检验记录
        if (order.getSourceId() != null) {
            switch (order.getSourceType()) {
                case "来料不良":
                    MaterialInspection mi = materialInspectionMapper.selectById(order.getSourceId());
                    vo.setMaterialInspection(mi);
                    break;
                case "首件不良":
                    FaiInspectionRecord fai = faiInspectionRecordMapper.selectById(order.getSourceId());
                    vo.setFaiInspection(fai);
                    break;
                case "成品不良":
                    FinishedGoodsInspection fgi = finishedGoodsInspectionMapper.selectById(order.getSourceId());
                    vo.setFinishedGoodsInspection(fgi);
                    break;
                default:
                    break;
            }
        }

        // 整改计划（与改善措施区分的独立对象）
        LambdaQueryWrapper<RectificationPlan> planWrapper = new LambdaQueryWrapper<>();
        planWrapper.eq(RectificationPlan::getExceptionId, id)
                .orderByDesc(RectificationPlan::getCreatedAt);
        vo.setRectificationPlans(rectificationPlanMapper.selectList(planWrapper));

        return vo;
    }

    // ===== 新增 =====

    @Override
    @Transactional
    public ExceptionOrder create(ExceptionOrder order) {
        LoginUser loginUser = getCurrentLoginUser();
        String plantCode = loginUser.getPlantCode().name();

        order.setPlantCode(plantCode);
        order.setPlantName(loginUser.getPlantCode().getChineseName());
        order.setStatus("待整改");
        if (order.getCapaStatus() == null) {
            order.setCapaStatus("待发起");
        }
        order.setExceptionNo(generateExceptionNo());
        order.setCreatedBy(loginUser.getRealName());
        order.setUpdatedBy(loginUser.getRealName());

        insertExceptionWithRetry(order);
        return order;
    }

    // ===== 根据来料检验记录自动生成异常单 =====

    @Override
    @Transactional
    public ExceptionOrder createFromMaterialInspection(MaterialInspection inspection, LoginUser loginUser) {
        // 异常单归属以来料记录自身厂为准，避免当前用户厂与来料真实厂错位（如 SZ 用户建 MZ 来料单）
        String plantCode = StringUtils.hasText(inspection.getPlantCode())
                ? inspection.getPlantCode() : loginUser.getPlantCode().name();

        Long existingId = findExceptionBySourceId(inspection.getId());
        if (existingId != null) {
            return exceptionOrderMapper.selectById(existingId);
        }

        LocalDate inspectionDate = inspection.getInspectionDate() != null
                ? inspection.getInspectionDate() : LocalDate.now();
        int repeatCount30Days = countRecentUnqualified(
                inspection, plantCode, inspectionDate.minusDays(29), inspectionDate);
        int repeatCount90Days = countRecentUnqualified(
                inspection, plantCode, inspectionDate.minusDays(89), inspectionDate);
        QualityExceptionDecisionVO decision = qualityRuleEvaluator.evaluate(inspection, repeatCount30Days);

        ExceptionOrder order = new ExceptionOrder();
        order.setSourceType("来料不良");
        order.setSourceId(inspection.getId());
        order.setSeverity(decision.getSeverity());
        order.setStatus("待整改");
        // processType 改为质量部门手动选择（发起时指定），自动建单不预填、不留推荐值
        order.setCapaStatus("待发起");
        order.setProcessType(null);
        order.setMaterialCode(inspection.getMaterialCode());
        order.setDefectDesc(inspection.getDefectDesc());
        order.setDefectQty(inspection.getUnqualifiedQty());
        order.setTotalQty(inspection.getSubmittedQty());
        order.setDeadline(LocalDate.now().plusDays(decision.getDeadlineDays()));
        order.setResponseDeadline(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(decision.getResponseHours()));
        order.setRuleReason(decision.getRuleReason());
        order.setNotificationLevel(decision.getNotificationLevel());
        order.setRepeatCount30Days(repeatCount30Days);
        order.setRepeatCount90Days(repeatCount90Days);
        order.setProblemFingerprint(buildProblemFingerprint(plantCode, inspection));
        order.setHandlingMethod(inspection.getHandlingMethod());
        order.setHandlerId(loginUser.getUserId());
        order.setExceptionNo(generateExceptionNo());
        order.setPlantCode(plantCode);
        order.setPlantName(StringUtils.hasText(inspection.getPlantName())
                ? inspection.getPlantName() : loginUser.getPlantCode().getChineseName());
        order.setCreatedBy(loginUser.getRealName());
        order.setUpdatedBy(loginUser.getRealName());

        // 根据 supplierCode 查找 supplierId，同时存储 supplierName 做容错
        if (StringUtils.hasText(inspection.getSupplierCode())) {
            LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Supplier::getSupplierCode, inspection.getSupplierCode())
                    .eq(Supplier::getPlantCode, plantCode);
            Supplier supplier = supplierMapper.selectOne(wrapper);
            if (supplier != null) {
                order.setSupplierId(supplier.getId());
                order.setSupplierName(supplier.getSupplierName());
            } else {
                // supplier 表中未找到时，兜底使用来料检验中的供应商名称
                order.setSupplierName(inspection.getSupplierName());
            }
        } else if (StringUtils.hasText(inspection.getSupplierName())) {
            order.setSupplierName(inspection.getSupplierName());
        }

        insertExceptionWithRetry(order);

        // 8D 报告在质量部门发起（initiate）时按所选流程类型初始化，自动建单阶段不预建

        sendExceptionCreatedNotifications(order, loginUser);
        createAutomaticEscalationIfNeeded(order, inspection, loginUser, repeatCount90Days);
        auditLogService.record(TABLE_NAME_EXCEPTION, order.getId(), "CREATE", null, order,
                "来料不良自动触发；" + decision.getRuleReason());

        log.info("来料不合格自动建异常单：materialInspectionId={}, exceptionNo={}, severity={}, processType={}",
                inspection.getId(), order.getExceptionNo(), order.getSeverity(), order.getProcessType());
        return order;
    }

    /**
     * 插入异常单并兜底单号并发重号：若唯一索引 uq_exo_no 冲突（并发生成相同单号），
     * 自动重新生成单号并重试，最多 3 次。对应缺陷 L15。
     */
    private void insertExceptionWithRetry(ExceptionOrder order) {
        int attempts = 0;
        while (true) {
            try {
                exceptionOrderMapper.insert(order);
                return;
            } catch (DuplicateKeyException e) {
                if (++attempts >= 3) {
                    throw e;
                }
                order.setExceptionNo(generateExceptionNo());
            }
        }
    }

    /**
     * 根据首件检验不合格记录自动生成异常单（对应缺陷 L30）。
     * 仅在尚未关联异常单时创建，避免 autoJudge 多次触发产生重复工单。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ExceptionOrder createFromFai(FaiInspectionRecord record, LoginUser loginUser) {
        ExceptionOrder existing = exceptionOrderMapper.selectOne(new LambdaQueryWrapper<ExceptionOrder>()
                .eq(ExceptionOrder::getSourceId, record.getId())
                .eq(ExceptionOrder::getSourceType, "首件不良")
                .eq(ExceptionOrder::getIsDeleted, 0));
        if (existing != null) {
            return existing;
        }
        String plantCode = StringUtils.hasText(record.getPlantCode())
                ? record.getPlantCode() : loginUser.getPlantCode().name();

        // 同一产品、同一厂区近30天首件不合格重复次数（用于严重等级评估）
        int repeatCount30Days = countRecentFaiUnqualified(
                plantCode, record, LocalDate.now().minusDays(29), LocalDate.now());
        QualityExceptionDecisionVO decision = qualityRuleEvaluator.evaluateFai(record, repeatCount30Days);

        ExceptionOrder order = new ExceptionOrder();
        order.setSourceType("首件不良");
        order.setSourceId(record.getId());
        order.setPlantCode(plantCode);
        order.setPlantName(StringUtils.hasText(record.getPlantName())
                ? record.getPlantName() : loginUser.getPlantCode().getChineseName());
        order.setStatus("待整改");
        order.setCapaStatus("待发起");
        order.setSeverity(decision.getSeverity());
        // processType 改为质量部门手动选择（发起时指定），自动建单不预填
        order.setProcessType(null);
        order.setNotificationLevel(decision.getNotificationLevel());
        order.setDeadline(LocalDate.now().plusDays(decision.getDeadlineDays()));
        order.setResponseDeadline(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(decision.getResponseHours()));
        order.setRuleReason(decision.getRuleReason());
        order.setRepeatCount30Days(repeatCount30Days);
        order.setExceptionNo(generateExceptionNo());
        order.setMaterialCode(record.getMaterialCode());
        order.setDefectDesc("首件检验不合格（FAI 记录 " + record.getFaiNo() + "），需发起整改");
        order.setCreatedBy(loginUser.getRealName());
        order.setUpdatedBy(loginUser.getRealName());
        insertExceptionWithRetry(order);

        // 8D 报告在质量部门发起（initiate）时按所选流程类型初始化，自动建单阶段不预建
        sendExceptionCreatedNotifications(order, loginUser);
        auditLogService.record(TABLE_NAME_EXCEPTION, order.getId(), "CREATE", null, order,
                "首件不良自动触发；" + decision.getRuleReason());

        log.info("首件不良自动建异常单：faiId={}, exceptionNo={}, severity={}, processType={}",
                record.getId(), order.getExceptionNo(), order.getSeverity(), order.getProcessType());
        return order;
    }

    /**
     * 根据成品入库检验不合格记录自动生成异常单（对应异常来源「成品不良」）。
     * 仅在品管审核通过、检验结论为不合格且不合格数量 > 0 时调用；按 reportNo 防重，避免重复建单。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public ExceptionOrder createFromFinishedGoods(FinishedGoodsInspection inspection, LoginUser loginUser) {
        ExceptionOrder existing = exceptionOrderMapper.selectOne(new LambdaQueryWrapper<ExceptionOrder>()
                .eq(ExceptionOrder::getSourceId, inspection.getId())
                .eq(ExceptionOrder::getSourceType, "成品不良")
                .eq(ExceptionOrder::getIsDeleted, 0));
        if (existing != null) {
            return existing;
        }
        String plantCode = StringUtils.hasText(inspection.getPlantCode())
                ? inspection.getPlantCode() : loginUser.getPlantCode().name();

        QualityExceptionDecisionVO decision = qualityRuleEvaluator.evaluateFinishedGoods(inspection);

        ExceptionOrder order = new ExceptionOrder();
        order.setSourceType("成品不良");
        order.setSourceId(inspection.getId());
        order.setPlantCode(plantCode);
        order.setPlantName(StringUtils.hasText(inspection.getPlantName())
                ? inspection.getPlantName() : loginUser.getPlantCode().getChineseName());
        order.setStatus("待整改");
        order.setCapaStatus("待发起");
        // processType 改为质量部门手动选择（发起时指定），自动建单不预填
        order.setProcessType(null);
        order.setSeverity(decision.getSeverity());
        order.setNotificationLevel(decision.getNotificationLevel());
        order.setMaterialCode(inspection.getMaterialCode());
        order.setDefectDesc("成品入库检验不合格（报告 " + inspection.getReportNo() + "，"
                + (inspection.getCategory() != null ? inspection.getCategory() : "成品") + "），需发起整改");
        order.setDefectQty(inspection.getUnqualifiedQty());
        order.setTotalQty(inspection.getInspectedQty());
        order.setDeadline(LocalDate.now().plusDays(decision.getDeadlineDays()));
        order.setResponseDeadline(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusHours(decision.getResponseHours()));
        order.setRuleReason(decision.getRuleReason());
        order.setExceptionNo(generateExceptionNo());
        order.setCreatedBy(loginUser.getRealName());
        order.setUpdatedBy(loginUser.getRealName());

        insertExceptionWithRetry(order);

        // 8D 报告在质量部门发起（initiate）时按所选流程类型初始化，自动建单阶段不预建
        sendExceptionCreatedNotifications(order, loginUser);
        auditLogService.record(TABLE_NAME_EXCEPTION, order.getId(), "CREATE", null, order,
                "成品不良自动触发；" + decision.getRuleReason());

        log.info("成品不良自动建异常单：finishedGoodsId={}, exceptionNo={}, severity={}, processType={}",
                inspection.getId(), order.getExceptionNo(), order.getSeverity(), order.getProcessType());
        return order;
    }

    /**
     * 统计同一厂区、同一产品/物料（按 itemType 取值）+ 同一工序在指定日期窗口内的首件不合格记录数（含当前记录，最小为 1）。
     * 与规则文案「同一产品、同一工序30天内重复不合格」的口径一致。
     */
    private int countRecentFaiUnqualified(String plantCode, FaiInspectionRecord record,
                                          LocalDate startDate, LocalDate endDate) {
        String itemCode = "PRODUCT".equals(record.getItemType()) ? record.getItemCode() : record.getMaterialCode();
        if (!StringUtils.hasText(itemCode)) {
            return 1;
        }
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);
        LambdaQueryWrapper<FaiInspectionRecord> q = new LambdaQueryWrapper<FaiInspectionRecord>()
                .eq(FaiInspectionRecord::getInspectionResult, "不合格")
                .eq(FaiInspectionRecord::getProcessCode, record.getProcessCode())
                .between(FaiInspectionRecord::getCreatedAt, start, end);
        if ("PRODUCT".equals(record.getItemType())) {
            q.eq(FaiInspectionRecord::getItemCode, itemCode);
        } else {
            q.eq(FaiInspectionRecord::getMaterialCode, itemCode);
        }
        Long count = faiInspectionRecordMapper.selectCount(q);
        return Math.max(1, count == null ? 1 : count.intValue());
    }

    // ===== 更新 =====

    @Override
    public void update(Long id, ExceptionUpdateDTO dto) {
        ExceptionOrder existing = exceptionOrderMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + id);
        }
        LoginUser loginUser = getCurrentLoginUser();
        // 分公司越权校验：禁止操作其它分公司的异常单
        if (!existing.getPlantCode().equals(loginUser.getPlantCode().name())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作其他分公司的异常单");
        }
        ExceptionOrder update = new ExceptionOrder();
        update.setId(id);
        // 乐观锁：回填版本号，触发 MyBatis-Plus @Version 并发保护（原代码漏设 version，并发更新无保护）
        update.setVersion(existing.getVersion());
        // 仅拷贝白名单内的可编辑字段，且非 null 才覆盖，避免误清空未提供的字段；
        // plantCode/exceptionNo/createdBy/status/closedAt 等系统字段绝不会被写入
        applyEditableFields(dto, update);
        update.setUpdatedBy(loginUser.getRealName());
        exceptionOrderMapper.updateById(update);
    }

    /** 将白名单字段从 DTO 拷贝到待更新实体；仅拷贝非 null 值，未提供的字段保留原值 */
    private void applyEditableFields(ExceptionUpdateDTO dto, ExceptionOrder target) {
        if (dto.getSourceType() != null) target.setSourceType(dto.getSourceType());
        if (dto.getSeverity() != null) target.setSeverity(dto.getSeverity());
        if (dto.getSupplierId() != null) target.setSupplierId(dto.getSupplierId());
        if (dto.getWorkOrderId() != null) target.setWorkOrderId(dto.getWorkOrderId());
        if (dto.getMaterialCode() != null) target.setMaterialCode(dto.getMaterialCode());
        if (dto.getDefectDesc() != null) target.setDefectDesc(dto.getDefectDesc());
        if (dto.getDefectQty() != null) target.setDefectQty(dto.getDefectQty());
        if (dto.getTotalQty() != null) target.setTotalQty(dto.getTotalQty());
        if (dto.getHandlerId() != null) target.setHandlerId(dto.getHandlerId());
        if (dto.getReviewerId() != null) target.setReviewerId(dto.getReviewerId());
        if (dto.getDeadline() != null) target.setDeadline(dto.getDeadline());
        if (dto.getRemark() != null) target.setRemark(dto.getRemark());
        if (dto.getSignatureUser() != null) target.setSignatureUser(dto.getSignatureUser());
        if (dto.getSignatureTime() != null) target.setSignatureTime(dto.getSignatureTime());
        if (dto.getSignatureReason() != null) target.setSignatureReason(dto.getSignatureReason());
    }

    // ===== 逻辑删除 =====

    @Override
    public void delete(Long id) {
        // 先按 id 加载并校验归属（拦截器自动注入 plant_code，他厂记录返回 null），避免越权静默删除
        ExceptionOrder existing = exceptionOrderMapper.selectById(id);
        if (existing == null) throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在");
        exceptionOrderMapper.deleteById(id);
    }

    // ===== 发起整改流程（选择 CAPA / 8D / BOTH） =====

    private static final List<String> VALID_PROCESS_TYPES = Arrays.asList("CAPA", "8D", "BOTH");

    @Override
    @Transactional
    public ExceptionOrder initiate(Long id, ExceptionInitiateDTO dto) {
        // 权限加固：发起整改流程需 R03/R04/R06（R00 超级管理员绕过）
        ExceptionModuleHelper.assertRole("R03", "R04", "R06");
        String processType = dto.getProcessType();
        if (!VALID_PROCESS_TYPES.contains(processType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "无效的整改流程类型：" + processType + "（应为 CAPA / 8D / BOTH）");
        }
        ExceptionOrder existing = exceptionOrderMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + id);
        }
        if (!"待发起".equals(existing.getCapaStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "仅「待发起」状态的异常单可发起流程（当前：" + existing.getCapaStatus() + "）");
        }

        LoginUser loginUser = getCurrentLoginUser();
        LocalDateTime now = LocalDateTime.now();

        ExceptionOrder update = new ExceptionOrder();
        update.setId(id);
        update.setProcessType(processType);
        update.setCapaStatus("进行中");
        // 状态机联动：发起即进入「整改中」，与后续「待验证」「已闭环」形成完整流转
        update.setStatus("整改中");
        // 乐观锁回填：防止并发双开发起互相覆盖 processType
        update.setVersion(existing.getVersion());
        update.setUpdatedBy(loginUser.getRealName());
        // 记录发起整改的「操作人」及时间（与 updatedBy 区分，便于追溯「谁发起」）
        update.setInitiatedBy(loginUser.getRealName());
        update.setInitiatedByUserId(loginUser.getUserId());
        update.setInitiatedAt(now);
        // 整改责任人：相关部门从已有人员中选择填写（区别于发起操作人；自动触发单发起时也可补填）
        update.setOwnerId(dto.getOwnerId());
        update.setOwnerName(dto.getOwnerName());
        // 含 8D 流程（8D 或 BOTH）：CAPA 立项阶段，8D 可并行推进 D1-D4
        // 8D 报告自身也需经 CAPA 根因审批 / 措施审批解锁 D5 / D6
        if (ExceptionModuleHelper.processIncludes8D(processType)) {
            update.setCapaPhase(ExceptionConstants.CAPA_PHASE_INITIATE);
        }
        exceptionOrderMapper.updateById(update);

        // D0 质量部发起：写发起说明 + 指定负责人，初始化 8D 记录（含 D0，currentStep=D1 等待负责人组建团队）
        existing.setProcessType(processType);
        ensureEightDInitialized(existing, loginUser, dto, now);

        // D0 通知：仅通知指定负责人
        notifyD0Leader(id, existing, dto, loginUser);

        // 审计：记录发起整改流程操作
        auditLogService.record(TABLE_NAME_EXCEPTION, id, ACTION_UPDATE, existing, update,
                "发起整改流程：" + processType + "；指定负责人=" + dto.getOwnerName());

        log.info("发起整改流程：exceptionId={}, processType={}, ownerName={}",
                id, processType, dto.getOwnerName());
        return exceptionOrderMapper.selectById(id);
    }

    // ===== CAPA 相位审批（BOTH 模式专用） =====

    private static final java.util.List<String> CAPA_PHASE_VALUES = java.util.Arrays.asList(
            ExceptionConstants.CAPA_PHASE_INITIATE,
            ExceptionConstants.CAPA_PHASE_ROOT_CAUSE_APPROVED,
            ExceptionConstants.CAPA_PHASE_MEASURES_APPROVED,
            ExceptionConstants.CAPA_PHASE_CLOSED);

    @Override
    @Transactional
    public void approveCapaRootCause(Long id, String comment) {
        approveCapaPhase(id, ExceptionConstants.CAPA_PHASE_ROOT_CAUSE_APPROVED, "根因分析", comment);
    }

    @Override
    @Transactional
    public void approveCapaMeasures(Long id, String comment) {
        approveCapaPhase(id, ExceptionConstants.CAPA_PHASE_MEASURES_APPROVED, "措施方案", comment);
    }

    /**
     * 统一的 CAPA 相位推进方法。
     * 校验 BOTH 模式 + 当前相位正确 + 目标相位合法，推进并记录审计日志。
     */
    private void approveCapaPhase(Long id, String targetPhase, String phaseLabel, String comment) {
        ExceptionOrder order = exceptionOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + id);
        }
        if (!ExceptionModuleHelper.processIncludes8D(order.getProcessType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅含 8D 报告（8D / BOTH）的流程需要 CAPA 相位审批，当前流程类型：" + order.getProcessType());
        }

        // 相位推进校验（不可回退、不可跳步）
        String currentPhase = order.getCapaPhase() != null ? order.getCapaPhase() : ExceptionConstants.CAPA_PHASE_INITIATE;
        String allowedFrom;
        if (ExceptionConstants.CAPA_PHASE_ROOT_CAUSE_APPROVED.equals(targetPhase)) {
            allowedFrom = ExceptionConstants.CAPA_PHASE_INITIATE;
        } else if (ExceptionConstants.CAPA_PHASE_MEASURES_APPROVED.equals(targetPhase)) {
            allowedFrom = ExceptionConstants.CAPA_PHASE_ROOT_CAUSE_APPROVED;
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的审批目标相位：" + targetPhase);
        }

        if (!allowedFrom.equals(currentPhase)) {
            String currentLabel = ExceptionConstants.CAPA_PHASE_INITIATE.equals(currentPhase) ? "CAPA 立项" : "根因已审批";
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "当前 CAPA 相位为「" + currentLabel + "」，不可执行" + phaseLabel + "审批。"
                            + "审批推进顺序：根因分析 → 根因审批 → 措施审批 → D6-D8 → 验证闭环");
        }

        // CAPA 审批门禁：按系统管理「审批配置」(CAPA 维度) 校验审批角色。
        // 审批动作 → 配置阶段码 映射（与审批配置页面 CAPA 维度一致）：
        //   根因审批(ROOT_CAUSE_APPROVED) → C1
        //   措施审批(MEASURES_APPROVED)   → C2
        // 仅当配置存在且 needApproval=1 时才限定审批角色，否则保持原有「任意有权限角色可审批」行为。
        String capaConfigStage = ExceptionConstants.CAPA_PHASE_ROOT_CAUSE_APPROVED.equals(targetPhase) ? "C1" : "C2";
        ExceptionApprovalConfig capaConfig = approvalConfigService.resolveConfig(
                "CAPA", capaConfigStage, order.getPlantCode());
        if (capaConfig != null && capaConfig.getNeedApproval() != null && capaConfig.getNeedApproval() == 1) {
            ExceptionModuleHelper.requireApprovalPrivilege(capaConfig.getApproverRole());
        }

        ExceptionOrder update = new ExceptionOrder();
        update.setId(id);
        update.setCapaPhase(targetPhase);
        LoginUser loginUser = getCurrentLoginUser();
        update.setUpdatedBy(loginUser.getRealName());

        exceptionOrderMapper.updateById(update);

        // 审计
        auditLogService.record(TABLE_NAME_EXCEPTION, id, ACTION_UPDATE, order, update,
                "CAPA " + phaseLabel + "审批通过：" + comment);

        log.info("CAPA 相位审批：exceptionId={}, phase={}→{}, operator={}, comment={}",
                id, currentPhase, targetPhase, loginUser.getRealName(), comment);

        // 通知：根因/措施审批通过（配置驱动）
        String notifCode = ExceptionConstants.CAPA_PHASE_ROOT_CAUSE_APPROVED.equals(targetPhase)
                ? NotificationTypeEnum.CAPA_ROOT_CAUSE_APPROVED.getCode()
                : NotificationTypeEnum.CAPA_MEASURES_APPROVED.getCode();
        notifyByConfig(order, notifCode,
                "CAPA " + phaseLabel + "审批通过",
                "异常单【" + order.getExceptionNo() + "】" + phaseLabel + "审批已通过。审批人："
                        + loginUser.getRealName() + "，意见：" + (comment != null ? comment : "无"));
    }

    @Override
    public List<ExceptionUserOptionVO> listUserOptions() {
        List<AdminUserVO> users = adminService.listUsers();
        if (users == null) {
            return Collections.emptyList();
        }
        LoginUser loginUser = getCurrentLoginUser();
        String plantCode = loginUser != null ? loginUser.getPlantCode().name() : null;
        return users.stream()
                // 数据隔离：仅返回当前分公司人员（R00 超级管理员可看全部）
                .filter(u -> plantCode == null || "R00".equals(loginUser.getRoleCode()) || plantCode.equals(u.getPlantCode()))
                .map(u -> {
                    ExceptionUserOptionVO vo = new ExceptionUserOptionVO();
                    vo.setId(u.getId());
                    vo.setRealName(u.getRealName());
                    vo.setRoleCode(u.getRoleCode());
                    vo.setPlantCode(u.getPlantCode());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    // ===== 闭环 =====

    @Override
    @Transactional
    public void close(Long id, ExceptionCloseDTO dto) {
        // 权限加固：闭环需 R06 质量经理（R00 超级管理员绕过）
        ExceptionModuleHelper.assertRole("R06");
        ExceptionOrder order = exceptionOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + id);
        }
        if ("已闭环".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "异常单已闭环，无需重复操作");
        }

        // 统一闭环前置条件校验（close 与 closeReadiness 共用，杜绝逻辑漂移）
        List<String> missing = evaluateCloseMissing(order);
        if (!missing.isEmpty()) {
            throw new BusinessException(ResultCode.CLOSE_PRECONDITION_NOT_MET,
                    "闭环前置条件不满足：" + String.join("；", missing));
        }

        // 执行闭环
        ExceptionOrder update = new ExceptionOrder();
        update.setId(id);
        update.setStatus("已闭环");
        update.setClosedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        update.setCapaStatus("已完成");
        // 含 8D 流程（8D 或 BOTH）：CAPA 治理流程闭环
        if (ExceptionModuleHelper.processIncludes8D(order.getProcessType())) {
            update.setCapaPhase(ExceptionConstants.CAPA_PHASE_CLOSED);
        }
        update.setRemark(order.getRemark() != null ? order.getRemark() + "\n闭环原因：" + dto.getCloseReason() : "闭环原因：" + dto.getCloseReason());
        LoginUser loginUser = getCurrentLoginUser();
        update.setUpdatedBy(loginUser.getRealName());

        exceptionOrderMapper.updateById(update);

        // 审计：记录线上闭环操作
        auditLogService.record(TABLE_NAME_EXCEPTION, id, ACTION_UPDATE, order, update, "线上闭环：" + dto.getCloseReason());

        // 发送状态变更通知
        sendExceptionStatusChangedNotification(order, loginUser, "已闭环");

        // CAPA 闭环通知（BOTH 模式：配置驱动通知发起整改人/质量审核人）
        if (ExceptionModuleHelper.processIncludes8D(order.getProcessType())) {
            notifyByConfig(order, NotificationTypeEnum.CAPA_CLOSED.getCode(),
                    "CAPA 闭环通知",
                    "异常单【" + order.getExceptionNo() + "】已完成闭环。操作人：" + loginUser.getRealName());
        }

        log.info("异常单 {} 已闭环，操作人：{}", id, loginUser.getRealName());
    }

    // ===== 闭环前置条件检查 =====

    @Override
    public CloseReadinessVO closeReadiness(Long id) {
        ExceptionOrder order = exceptionOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + id);
        }
        // 复用统一校验方法，构建明细检查项
        List<CloseReadinessVO.CheckItem> checks = evaluateCloseChecks(order);
        boolean canClose = checks.stream().noneMatch(c -> "FAIL".equals(c.getStatus()));
        CloseReadinessVO result = new CloseReadinessVO();
        result.setCanClose(canClose);
        result.setChecks(checks);
        return result;
    }

    // ===== CAPA 相位审批就绪检查 =====

    @Override
    public CapaPhaseApprovalReadinessVO capaPhaseApprovalReadiness(Long id) {
        ExceptionOrder order = exceptionOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + id);
        }
        if (!ExceptionModuleHelper.processIncludes8D(order.getProcessType())) {
            return CapaPhaseApprovalReadinessVO.cannotApprove(order.getCapaPhase(),
                    "非 BOTH 模式，无需 CAPA 相位审批");
        }
        String phase = order.getCapaPhase();
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null) {
            return CapaPhaseApprovalReadinessVO.cannotApprove(phase, "无法获取当前登录用户");
        }

        // 只有发起整改人（质量审核人）可以审批
        boolean isInitiator = loginUser.getUserId().equals(order.getInitiatedByUserId());

        if (ExceptionConstants.CAPA_PHASE_ROOT_CAUSE_APPROVED.equals(phase)) {
            if (isInitiator) {
                return CapaPhaseApprovalReadinessVO.canApprove(phase, "待根因审批");
            }
            return CapaPhaseApprovalReadinessVO.cannotApprove(phase,
                    "仅发起整改人可审批根因，当前用户：" + loginUser.getRealName());
        }

        if (ExceptionConstants.CAPA_PHASE_MEASURES_APPROVED.equals(phase)) {
            if (isInitiator) {
                return CapaPhaseApprovalReadinessVO.canApprove(phase, "待措施审批");
            }
            return CapaPhaseApprovalReadinessVO.cannotApprove(phase,
                    "仅发起整改人可审批措施，当前用户：" + loginUser.getRealName());
        }

        if (ExceptionConstants.CAPA_PHASE_CLOSED.equals(phase)) {
            return CapaPhaseApprovalReadinessVO.cannotApprove(phase, "CAPA 相位已闭环，无需审批");
        }

        return CapaPhaseApprovalReadinessVO.cannotApprove(phase,
                "当前相位（" + phase + "）无需审批，请检查 8D 步骤是否已正确推进");
    }

    /**
     * 统一的闭环前置条件校验：5 项检查，每项返回 PASS/FAIL/NA + 说明。
     */
    private List<CloseReadinessVO.CheckItem> evaluateCloseChecks(ExceptionOrder order) {
        List<CloseReadinessVO.CheckItem> checks = new ArrayList<>();
        checks.add(checkRectificationProcess(order));
        checks.add(checkRectificationPlans(order));
        checks.add(checkImprovementActions(order));
        checks.add(checkVerificationRecords(order));
        checks.add(check8DReport(order));
        return checks;
    }

    /** 检查 1：整改流程是否已发起 */
    private CloseReadinessVO.CheckItem checkRectificationProcess(ExceptionOrder order) {
        CloseReadinessVO.CheckItem item = new CloseReadinessVO.CheckItem();
        item.setItem("整改流程");
        if (order.getProcessType() != null && !"待发起".equals(order.getCapaStatus())) {
            item.setStatus("PASS");
            item.setDetail("已发起（" + order.getProcessType() + "，" + order.getCapaStatus() + "）");
        } else {
            item.setStatus("FAIL");
            item.setDetail("尚未发起整改流程");
        }
        return item;
    }

    /** 检查 2：整改计划（CAPA/BOTH 模式需要） */
    private CloseReadinessVO.CheckItem checkRectificationPlans(ExceptionOrder order) {
        Long id = order.getId();
        CloseReadinessVO.CheckItem item = new CloseReadinessVO.CheckItem();
        item.setItem("整改计划");

        LambdaQueryWrapper<RectificationPlan> planWrapper = new LambdaQueryWrapper<>();
        planWrapper.eq(RectificationPlan::getExceptionId, id);
        List<RectificationPlan> plans = rectificationPlanMapper.selectList(planWrapper);
        long planTotal = plans.size();
        long planDone = plans.stream().filter(p -> "已完成".equals(p.getStatus())).count();

        boolean capaOrBoth = order.getProcessType() != null
                && (ExceptionConstants.PROCESS_CAPA.equals(order.getProcessType())
                    || ExceptionConstants.PROCESS_BOTH.equals(order.getProcessType()));

        if (!capaOrBoth && planTotal == 0) {
            item.setStatus("NA");
            item.setDetail("纯8D模式，不需要整改计划");
        } else if (planTotal == 0) {
            item.setStatus("FAIL");
            item.setDetail("尚未制定整改计划");
        } else if (planDone >= planTotal) {
            item.setStatus("PASS");
            item.setDetail(planDone + "/" + planTotal + " 已完成");
        } else {
            item.setStatus("FAIL");
            item.setDetail(planDone + "/" + planTotal + " 已完成");
        }
        return item;
    }

    /** 检查 3：改善措施（全部 DONE 才通过） */
    private CloseReadinessVO.CheckItem checkImprovementActions(ExceptionOrder order) {
        Long id = order.getId();
        CloseReadinessVO.CheckItem item = new CloseReadinessVO.CheckItem();
        item.setItem("改善措施");

        LambdaQueryWrapper<ImprovementAction> actionWrapper = new LambdaQueryWrapper<>();
        actionWrapper.eq(ImprovementAction::getExceptionId, id);
        List<ImprovementAction> actions = improvementActionMapper.selectList(actionWrapper);
        long actionTotal = actions.size();
        long actionDone = actions.stream().filter(a -> ExceptionConstants.ACTION_STATUS_DONE.equals(a.getStatus())).count();

        if (actionTotal == 0) {
            item.setStatus("FAIL");
            item.setDetail("尚无改善措施");
        } else if (actionDone >= actionTotal) {
            item.setStatus("PASS");
            item.setDetail(actionDone + "/" + actionTotal + " DONE");
        } else {
            item.setStatus("FAIL");
            item.setDetail(actionDone + "/" + actionTotal + " DONE，" + (actionTotal - actionDone) + "条PENDING");
        }
        return item;
    }

    /** 检查 4：验证记录（最新一条须通过 + 时序合规） */
    private CloseReadinessVO.CheckItem checkVerificationRecords(ExceptionOrder order) {
        Long id = order.getId();
        CloseReadinessVO.CheckItem item = new CloseReadinessVO.CheckItem();
        item.setItem("验证记录");

        LambdaQueryWrapper<VerificationRecord> verifyWrapper = new LambdaQueryWrapper<>();
        verifyWrapper.eq(VerificationRecord::getExceptionId, id)
                .orderByDesc(VerificationRecord::getVerifyDate)
                .orderByDesc(VerificationRecord::getId);
        List<VerificationRecord> verifs = verificationRecordMapper.selectList(verifyWrapper);

        if (verifs.isEmpty()) {
            item.setStatus("FAIL");
            item.setDetail("尚无验证记录");
            return item;
        }

        VerificationRecord latest = verifs.get(0);
        if (!"通过".equals(latest.getResult())) {
            item.setStatus("FAIL");
            item.setDetail(verifs.size() + "条验证，最新结果「不通过」");
        } else if (latest.getVerifierName() == null || latest.getVerifierName().trim().isEmpty()) {
            item.setStatus("FAIL");
            item.setDetail("最新验证结果「通过」，但验证人为空");
        } else if (!verifyTimingCompliant(id, latest.getVerifyDate())) {
            item.setStatus("FAIL");
            item.setDetail("验证日期早于改善措施完成时间，时序不合规");
        } else {
            item.setStatus("PASS");
            item.setDetail(verifs.size() + "条验证，最新结果「通过」");
        }
        return item;
    }

    /** 检查验证时序：验证日期须晚于所有已完成改善措施的完成时间 */
    private boolean verifyTimingCompliant(Long exceptionId, LocalDate verifyDate) {
        LambdaQueryWrapper<ImprovementAction> actionWrapper = new LambdaQueryWrapper<>();
        actionWrapper.eq(ImprovementAction::getExceptionId, exceptionId)
                .eq(ImprovementAction::getStatus, ExceptionConstants.ACTION_STATUS_DONE);
        List<ImprovementAction> doneActions = improvementActionMapper.selectList(actionWrapper);
        for (ImprovementAction act : doneActions) {
            if (act.getCompletedAt() != null && verifyDate != null
                    && verifyDate.isBefore(act.getCompletedAt().toLocalDate())) {
                return false;
            }
        }
        return true;
    }

    /** 检查 5：8D 报告（含 8D 时须 D1~D8 全部完成） */
    private CloseReadinessVO.CheckItem check8DReport(ExceptionOrder order) {
        Long id = order.getId();
        CloseReadinessVO.CheckItem item = new CloseReadinessVO.CheckItem();
        item.setItem("8D报告");

        if (order.getProcessType() == null || !ExceptionModuleHelper.processIncludes8D(order.getProcessType())) {
            item.setStatus("NA");
            item.setDetail("未启用8D流程");
            return item;
        }

        Exception8d eightD = exception8dMapper.selectByExceptionId(id);
        if (eightD == null) {
            item.setStatus("FAIL");
            item.setDetail("8D 报告未创建");
            return item;
        }
        if (!ExceptionConstants.D8.equals(eightD.getCurrentStep())) {
            item.setStatus("FAIL");
            item.setDetail("当前步骤：" + eightD.getCurrentStep() + "，需走完 D8");
            return item;
        }

        // BOTH 模式：除 D8 完成外，CAPA 治理流程必须已完成根因+措施审批（相位到达 MEASURES_APPROVED）
        if (ExceptionConstants.PROCESS_BOTH.equals(order.getProcessType())
                && !ExceptionConstants.CAPA_PHASE_MEASURES_APPROVED.equals(order.getCapaPhase())) {
            item.setStatus("FAIL");
            item.setDetail("BOTH 模式要求 CAPA 治理流程已完成根因审批→措施审批，当前相位："
                    + (order.getCapaPhase() != null ? order.getCapaPhase() : "未设置"));
            return item;
        }

        List<String> unfilled = findUnfilled8DSteps(eightD);
        if (!unfilled.isEmpty()) {
            item.setStatus("FAIL");
            item.setDetail("D8已到达，但以下步骤未填写：" + String.join("、", unfilled));
        } else {
            item.setStatus("PASS");
            item.setDetail("8D D1~D8 全部完成");
        }
        return item;
    }

    /** 查找 8D 报告中未填写的步骤 */
    private List<String> findUnfilled8DSteps(Exception8d eightD) {
        List<String> unfilled = new ArrayList<>();
        if (isBlankStepContent(eightD.getD1Members()) && isBlankStepContent(eightD.getD1Team())) unfilled.add("D1");
        if (isBlankStepContent(eightD.getD2ProblemDesc())) unfilled.add("D2");
        if (isBlankStepContent(eightD.getD3Containment())) unfilled.add("D3");
        if (isBlankStepContent(eightD.getD4RootCause())) unfilled.add("D4");
        if (isBlankStepContent(eightD.getD5Corrective())) unfilled.add("D5");
        if (isBlankStepContent(eightD.getD6Implementation())) unfilled.add("D6");
        if (isBlankStepContent(eightD.getD7Preventive())) unfilled.add("D7");
        if (isBlankStepContent(eightD.getD8Closure())) unfilled.add("D8");
        return unfilled;
    }

    private boolean isBlankStepContent(String content) {
        return content == null || content.trim().isEmpty();
    }

    /**
     * 闭环前置缺失项（供 close 使用）：从统一校验中提取所有 FAIL 项的说明。
     */
    private List<String> evaluateCloseMissing(ExceptionOrder order) {
        List<String> missing = new ArrayList<>();
        for (CloseReadinessVO.CheckItem item : evaluateCloseChecks(order)) {
            if ("FAIL".equals(item.getStatus())) {
                missing.add(item.getItem() + "：" + item.getDetail());
            }
        }
        return missing;
    }

    // ===== 根据来源ID查找异常单 =====

    @Override
    public Long findExceptionBySourceId(Long sourceId) {
        LambdaQueryWrapper<ExceptionOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExceptionOrder::getSourceId, sourceId)
                .eq(ExceptionOrder::getSourceType, "来料不良")
                .orderByAsc(ExceptionOrder::getId)
                .last("LIMIT 1");
        ExceptionOrder order = exceptionOrderMapper.selectOne(wrapper);
        return order != null ? order.getId() : null;
    }

    // ===== 根据来料检验 ID 创建异常单 =====

    @Override
    @Transactional
    public ExceptionOrder createFromInspectionId(Long inspectionId) {
        // 检查是否已存在关联异常单
        Long existingId = findExceptionBySourceId(inspectionId);
        if (existingId != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该检验记录已关联异常单（ID：" + existingId + "），请勿重复创建");
        }

        MaterialInspection inspection = materialInspectionMapper.selectById(inspectionId);
        if (inspection == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "来料检验记录不存在：" + inspectionId);
        }
        if (!"不合格".equals(inspection.getInspectionResult())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅不合格记录可创建异常整改单");
        }

        LoginUser loginUser = getCurrentLoginUser();
        return self.createFromMaterialInspection(inspection, loginUser);
    }

    // ===== 重置异常单为最初状态 =====

    @Override
    @Transactional
    public void reset(Long id) {
        ExceptionOrder order = exceptionOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + id);
        }
        if (!"已闭环".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅「已闭环」状态的异常单可重置");
        }

        LoginUser loginUser = getCurrentLoginUser();

        // 逻辑删除关联的改善措施
        improvementActionMapper.update(null, new LambdaUpdateWrapper<ImprovementAction>()
                .eq(ImprovementAction::getExceptionId, id)
                .set(ImprovementAction::getIsDeleted, (short) 1));

        // 逻辑删除关联的验证记录
        verificationRecordMapper.update(null, new LambdaUpdateWrapper<VerificationRecord>()
                .eq(VerificationRecord::getExceptionId, id)
                .set(VerificationRecord::getIsDeleted, (short) 1));

        // 逻辑删除关联的 8D 报告
        exception8dMapper.update(null, new LambdaUpdateWrapper<Exception8d>()
                .eq(Exception8d::getExceptionId, id)
                .set(Exception8d::getIsDeleted, (short) 1));

        // 逻辑删除关联的整改计划
        rectificationPlanMapper.update(null, new LambdaUpdateWrapper<RectificationPlan>()
                .eq(RectificationPlan::getExceptionId, id)
                .set(RectificationPlan::getIsDeleted, (short) 1));

        // 重置异常单本身（用 LambdaUpdateWrapper 显式 set 以穿透 MyBatis-Plus FieldStrategy.NOT_NULL 的 null 过滤）
        exceptionOrderMapper.update(null, new LambdaUpdateWrapper<ExceptionOrder>()
                .eq(ExceptionOrder::getId, id)
                .set(ExceptionOrder::getStatus, "待整改")
                .set(ExceptionOrder::getCapaStatus, "待发起")
                .set(ExceptionOrder::getProcessType, null)
                .set(ExceptionOrder::getCapaPhase, null)
                .set(ExceptionOrder::getClosedAt, null)
                .set(ExceptionOrder::getUpdatedBy, loginUser.getRealName()));

        // 审计
        auditLogService.record(TABLE_NAME_EXCEPTION, id, ACTION_UPDATE, order,
                "status=待整改 capaStatus=待发起 processType=NULL capaPhase=NULL closedAt=NULL", "重置为最初状态（手动测试）");

        log.info("异常单 {} 已重置为最初状态，操作人：{}", id, loginUser.getRealName());
    }

    // ===== 审核追溯（聚合审计日志） =====

    @Override
    public List<AuditLog> auditTrail(Long id) {
        // 校验异常单归属本厂（拦截器已按 plant_code 过滤，他厂 id 返回 null），避免越权读取他厂审计日志
        if (exceptionOrderMapper.selectById(id) == null)
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在");
        List<AuditLog> result = new ArrayList<>();

        // 异常单自身
        result.addAll(auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getTableName, TABLE_NAME_EXCEPTION)
                .eq(AuditLog::getRecordId, id)));

        // 改善措施
        List<Long> actionIds = improvementActionMapper.selectList(
                        new LambdaQueryWrapper<ImprovementAction>().eq(ImprovementAction::getExceptionId, id)
                                .select(ImprovementAction::getId))
                .stream().map(ImprovementAction::getId).collect(Collectors.toList());
        if (!actionIds.isEmpty()) {
            result.addAll(auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                    .eq(AuditLog::getTableName, "improvement_action")
                    .in(AuditLog::getRecordId, actionIds)));
        }

        // 验证记录
        List<Long> verifyIds = verificationRecordMapper.selectList(
                        new LambdaQueryWrapper<VerificationRecord>().eq(VerificationRecord::getExceptionId, id)
                                .select(VerificationRecord::getId))
                .stream().map(VerificationRecord::getId).collect(Collectors.toList());
        if (!verifyIds.isEmpty()) {
            result.addAll(auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                    .eq(AuditLog::getTableName, "verification_record")
                    .in(AuditLog::getRecordId, verifyIds)));
        }

        // 8D 报告
        Exception8d eightD = exception8dMapper.selectByExceptionId(id);
        if (eightD != null) {
            result.addAll(auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                    .eq(AuditLog::getTableName, "exception_8d")
                    .eq(AuditLog::getRecordId, eightD.getId())));
        }

        // 整改计划
        List<Long> planIds = rectificationPlanMapper.selectList(
                        new LambdaQueryWrapper<RectificationPlan>().eq(RectificationPlan::getExceptionId, id)
                                .select(RectificationPlan::getId))
                .stream().map(RectificationPlan::getId).collect(Collectors.toList());
        if (!planIds.isEmpty()) {
            result.addAll(auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                    .eq(AuditLog::getTableName, "rectification_plan")
                    .in(AuditLog::getRecordId, planIds)));
        }

        // 按操作时间倒序
        result.sort((a, b) -> {
            LocalDateTime t1 = a.getOperationTime();
            LocalDateTime t2 = b.getOperationTime();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });
        return result;
    }

    // ===== KPI 看板统计 =====

    @Override
    public ExceptionStatsVO stats() {
        String plantCode = ExceptionModuleHelper.currentPlantCodeSafe();
        ExceptionStatsVO stats = exceptionOrderMapper.selectStats(plantCode);
        if (stats == null) {
            stats = new ExceptionStatsVO();
            stats.setTotalExceptions(0);
            stats.setPendingCount(0);
            stats.setInProgressCount(0);
            stats.setPendingVerifyCount(0);
            stats.setClosedCount(0);
            stats.setClosureRate(BigDecimal.ZERO);
            stats.setOverdueCount(0);
            stats.setEscalationCount(0);
        }
        stats.setSeverityBreakdown(exceptionOrderMapper.selectSeverityBreakdown(plantCode));
        stats.setSourceBreakdown(exceptionOrderMapper.selectSourceBreakdown(plantCode));
        return stats;
    }

    // ===== 多维度分析 =====

    @Override
    public ExceptionAnalysisVO analysis(String dimension) {
        String plantCode = ExceptionModuleHelper.currentPlantCodeSafe();
        List<ExceptionAnalysisItemVO> items = exceptionOrderMapper.selectAnalysis(plantCode, dimension);

        // 计算 ratio
        int total = items.stream().mapToInt(ExceptionAnalysisItemVO::getCount).sum();
        items.forEach(item -> item.setRatio(
                BigDecimal.valueOf(total == 0 ? 0 : item.getCount() * 100.0 / total)
                        .setScale(2, RoundingMode.HALF_UP)
        ));

        ExceptionAnalysisVO result = new ExceptionAnalysisVO();
        result.setDimension(dimension);
        result.setItems(items);
        return result;
    }

    @Override
    public QualityRuleCatalogVO qualityRules() {
        return qualityRuleEvaluator.catalog();
    }

    // ===== 供应商来料不良频次汇总 =====

    @Override
    public List<SupplierExceptionSummaryVO> supplierSummary(Long supplierId, String startDate, String endDate,
                                                              Integer minCount) {
        String plantCode = ExceptionModuleHelper.currentPlantCodeSafe();
        List<SupplierExceptionSummaryVO> list = exceptionOrderMapper.selectSupplierSummary(
                plantCode, supplierId, startDate, endDate, minCount != null ? minCount : 1);

        // 过滤 supplier_id 为空的记录（未关联供应商的异常单不应出现在供应商频次汇总中）
        list.removeIf(vo -> vo.getSupplierName() == null || vo.getSupplierName().isEmpty());
        for (SupplierExceptionSummaryVO vo : list) {
            if (vo.getRelatedExceptionIdsStr() != null && !vo.getRelatedExceptionIdsStr().isEmpty()) {
                vo.setRelatedExceptionIds(
                        Arrays.stream(vo.getRelatedExceptionIdsStr().split(","))
                                .map(String::trim)
                                .map(Long::parseLong)
                                .collect(Collectors.toList())
                );
            } else {
                vo.setRelatedExceptionIds(Collections.emptyList());
            }
        }
        return list;
    }

    // ===== 私有方法 =====

    private void sendExceptionCreatedNotifications(ExceptionOrder order, LoginUser loginUser) {
        try {
            String scenarioCode = NotificationTypeEnum.EXCEPTION_CREATED.getCode();
            List<String> roleCodes = notificationConfigService.getReceivingRoleCodes(scenarioCode);
            if (roleCodes.isEmpty()) {
                return;
            }

            // 严重等级追加角色
            if ("严重".equals(order.getSeverity())) {
                List<String> extraRoles = notificationConfigService.getSeverityExtraRoleCodes(scenarioCode);
                if (!extraRoles.isEmpty()) {
                    roleCodes = new ArrayList<>(roleCodes);
                    for (String er : extraRoles) {
                        if (!roleCodes.contains(er)) {
                            roleCodes.add(er);
                        }
                    }
                }
            }

            List<Long> recipientIds = notificationConfigService.listUserIdsByRoleCodes(roleCodes, order.getPlantCode());
            if (recipientIds.isEmpty()) {
                return;
            }
            if (loginUser.getUserId() != null && !recipientIds.contains(loginUser.getUserId())) {
                recipientIds.add(loginUser.getUserId());
            }

            for (Long userId : recipientIds) {
                NotificationCreateDTO dto = NotificationTemplateHelper.forExceptionCreated(
                        userId, order.getPlantCode(), order, loginUser.getRealName());
                notificationService.createNotification(dto);
            }
        } catch (Exception e) {
            log.warn("发送异常单创建通知失败：exceptionId={}", order.getId(), e);
        }
    }

    /**
     * D0 指定负责人通知：发起整改时通知指定的整改负责人，提示其进入 D1 组建团队。
     * 同时按场景配置抄送额外角色。
     */
    private void notifyD0Leader(Long exceptionId, ExceptionOrder order, ExceptionInitiateDTO dto, LoginUser loginUser) {
        try {
            if (dto.getOwnerId() == null) {
                return;
            }

            String scenarioCode = NotificationTypeEnum.EIGHT_D_LEADER_ASSIGNED.getCode();
            String level = "提醒";

            // 1. 点对点通知被指派人
            NotificationCreateDTO n = NotificationTemplateHelper.for8DLeaderAssigned(
                    dto.getOwnerId(), order.getPlantCode(), order, loginUser.getRealName());
            n.setContent("您被指定为异常单 " + order.getExceptionNo()
                    + " 的整改负责人（流程：" + dto.getProcessType() + "），请在 D1 阶段组建团队。");
            notificationService.createNotification(n);

            // 2. 按通知配置抄送额外角色（跳过被指派人，避免重复）
            List<String> ccRoles = notificationConfigService.getReceivingRoleCodes(scenarioCode);
            if (!ccRoles.isEmpty()) {
                List<Long> ccUserIds = notificationConfigService.listUserIdsByRoleCodes(ccRoles, order.getPlantCode());
                for (Long ccUserId : ccUserIds) {
                    if (ccUserId.equals(dto.getOwnerId())) {
                        continue;
                    }
                    NotificationCreateDTO cc = new NotificationCreateDTO();
                    cc.setUserId(ccUserId);
                    cc.setType(scenarioCode);
                    cc.setLevel(level);
                    cc.setTitle("【抄送】异常单 " + order.getExceptionNo() + " 已指定整改负责人");
                    cc.setContent("异常单【" + order.getExceptionNo() + "】（流程：" + dto.getProcessType()
                            + "）已指定 " + dto.getOwnerName() + " 为整改负责人，请在 D1 阶段组建团队。");
                    cc.setBusinessType(BUSINESS_TYPE_EXCEPTION);
                    cc.setBusinessId(exceptionId);
                    cc.setPlantCode(order.getPlantCode());
                    cc.setCreatedBy(loginUser.getRealName());
                    notificationService.createNotification(cc);
                }
            }
        } catch (Exception e) {
            log.warn("发送 D0 负责人通知失败：exceptionId={}", exceptionId, e);
        }
    }

    /**
     * 解析 JSON 数组字符串为姓名列表（["张三","李四"] → [张三, 李四]）。
     */
    private List<String> parseNameList(String json) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(json)) return result;
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        for (String s : trimmed.split(",")) {
            s = s.trim().replace("\"", "").replace("'", "");
            if (StringUtils.hasText(s)) {
                result.add(s);
            }
        }
        return result;
    }

    private int countRecentUnqualified(MaterialInspection inspection, String plantCode,
                                       LocalDate startDate, LocalDate endDate) {
        if (!StringUtils.hasText(inspection.getSupplierCode())
                || !StringUtils.hasText(inspection.getMaterialCode())) {
            return 1;
        }
        return Math.max(1, materialInspectionMapper.countUnqualifiedBatches(
                plantCode, inspection.getSupplierCode(), inspection.getMaterialCode(), startDate, endDate));
    }

    private String buildProblemFingerprint(String plantCode, MaterialInspection inspection) {
        if (!StringUtils.hasText(inspection.getSupplierCode())
                || !StringUtils.hasText(inspection.getMaterialCode())) {
            return null;
        }
        return plantCode + "|" + inspection.getSupplierCode().trim() + "|" + inspection.getMaterialCode().trim();
    }

    private void initializeEightD(ExceptionOrder order, LoginUser loginUser,
                                   ExceptionInitiateDTO dto, LocalDateTime initiateTime) {
        Exception8d eightD = new Exception8d();
        eightD.setExceptionId(order.getId());
        // D0 质量部发起：立案说明 + 发起人 + 发起时间 + 指定负责人
        // currentStep=D1：等待指定负责人自行组建团队并提交质量部审核
        eightD.setCurrentStep("D1");
        eightD.setStepStatus("DRAFT");
        eightD.setD0Symptom(dto.getD0Symptom());
        eightD.setD0Initiator(dto.getD0Initiator() != null ? dto.getD0Initiator() : loginUser.getRealName());
        eightD.setD0InitiateTime(initiateTime);
        // 发起时指定的 8D 团队 / CAPA 负责人同步预填进 8D 报告（避免与异常单详情指派不一致）
        eightD.setD1Team(dto.getD1Team());
        eightD.setCapaOwner(dto.getCapaOwner());
        // 选 CAPA / BOTH 时维护 CAPA 当前阶段 C1
        if ("CAPA".equals(order.getProcessType()) || "BOTH".equals(order.getProcessType())) {
            eightD.setCapaCurrentStep("C1");
        }
        eightD.setPlantCode(order.getPlantCode());
        eightD.setPlantName(order.getPlantName());
        eightD.setCreatedBy(loginUser.getRealName());
        eightD.setUpdatedBy(loginUser.getRealName());
        exception8dMapper.insert(eightD);
    }

    /**
     * 幂等确保 8D 报告存在：仅当 processType 含 8D 时生效。
     * ① 已存在则跳过；② 已软删除则恢复为 D1 初始状态；
     * ③ 不存在则新建 D0（含发起说明与指定负责人）。供发起流程入口安全复用。
     */
    private void ensureEightDInitialized(ExceptionOrder order, LoginUser loginUser,
                                         ExceptionInitiateDTO dto, LocalDateTime initiateTime) {
        if (!ExceptionModuleHelper.processIncludes8D(order.getProcessType())) {
            return;
        }
        Exception8d existing = exception8dMapper.selectByExceptionId(order.getId());
        if (existing != null) {
            return; // 已存在，跳过
        }
        Exception8d deleted = exception8dMapper.selectByExceptionIdIgnoreDeleted(order.getId());
        if (deleted != null) {
            // 已软删除则恢复为初始 D1 状态（保留发起阶段填写的 D0 立案信息与已指派团队）
            exception8dMapper.restoreDeletedById(deleted.getId());
            // 用 UpdateWrapper 显式 set null，绕过 MyBatis-Plus 默认 NOT_NULL 策略
            // （否则 setXxx(null) 的字段不会被 updateById 更新，D2-D8 历史值无法清空）
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Exception8d> uw =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            uw.eq("id", deleted.getId());
            uw.set("current_step", "D1");
            uw.set("step_status", "DRAFT");
            uw.set("d1_team", null);
            uw.set("d1_members", null);
            uw.set("d2_problem_desc", null);
            uw.set("d3_containment", null);
            uw.set("d4_root_cause", null);
            uw.set("d5_corrective", null);
            uw.set("d6_implementation", null);
            uw.set("d7_preventive", null);
            uw.set("d8_closure", null);
            // 保留 D0 立案说明与发起责任人、发起时指派的 8D 团队/CAPA 负责人
            uw.set("capa_owner", deleted.getCapaOwner());
            uw.set("capa_current_step", "CAPA".equals(order.getProcessType()) || "BOTH".equals(order.getProcessType()) ? "C1" : null);
            uw.set("is_deleted", 0);
            uw.set("updated_by", loginUser.getRealName());
            exception8dMapper.update(null, uw);
            return;
        }
        initializeEightD(order, loginUser, dto, initiateTime);
    }

    private void createAutomaticEscalationIfNeeded(ExceptionOrder order, MaterialInspection inspection,
                                                    LoginUser loginUser, int repeatCount90Days) {
        if (repeatCount90Days < 3 || order.getSupplierId() == null || !StringUtils.hasText(inspection.getSupplierCode())
                || !StringUtils.hasText(inspection.getMaterialCode())) {
            return;
        }

        LambdaQueryWrapper<Escalation> openWrapper = new LambdaQueryWrapper<>();
        openWrapper.eq(Escalation::getPlantCode, order.getPlantCode())
                .eq(Escalation::getSupplierCode, inspection.getSupplierCode())
                .eq(Escalation::getMaterialCode, inspection.getMaterialCode())
                .in(Escalation::getStatus, Arrays.asList("PENDING_REVIEW", "ACTIVE"));
        if (escalationMapper.selectCount(openWrapper) > 0) {
            return;
        }

        LambdaQueryWrapper<Escalation> historyWrapper = new LambdaQueryWrapper<>();
        historyWrapper.eq(Escalation::getPlantCode, order.getPlantCode())
                .eq(Escalation::getSupplierCode, inspection.getSupplierCode())
                .eq(Escalation::getMaterialCode, inspection.getMaterialCode())
                .ge(Escalation::getCreatedAt, LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(180));
        long previousCount = escalationMapper.selectCount(historyWrapper);

        Escalation escalation = new Escalation();
        escalation.setSupplierId(String.valueOf(order.getSupplierId()));
        escalation.setSupplierCode(inspection.getSupplierCode());
        escalation.setSupplierName(inspection.getSupplierName());
        escalation.setMaterialCode(inspection.getMaterialCode());
        escalation.setEscalationReason("同一供应商、同一物料90天内不合格"
                + repeatCount90Days + "批，达到自动升级阈值3批");
        escalation.setRelatedExceptionIds(String.valueOf(order.getId()));
        String escalationActionStr;
        if (previousCount == 0) {
            escalationActionStr = "加严检验、提高审核频次、专项8D";
        } else if (previousCount == 1) {
            escalationActionStr = "建议降低采购份额20%";
        } else {
            escalationActionStr = "建议暂停新增采购或暂停供货";
        }
        escalation.setEscalationAction(escalationActionStr);
        escalation.setStatus("PENDING_REVIEW");
        escalation.setPlantCode(order.getPlantCode());
        escalation.setPlantName(order.getPlantName());
        escalation.setCreatedBy("SYSTEM");
        escalation.setUpdatedBy("SYSTEM");
        escalationMapper.insert(escalation);

        sendEscalationNotifications(escalation, loginUser);
        auditLogService.record("escalation", escalation.getId(), "CREATE", null, escalation,
                "重复来料不良自动触发升级审核");
    }

    private void sendEscalationNotifications(Escalation escalation, LoginUser loginUser) {
        try {
            String scenarioCode = NotificationTypeEnum.ESCALATION_TRIGGERED.getCode();
            List<String> roleCodes = notificationConfigService.getReceivingRoleCodes(scenarioCode);
            if (roleCodes.isEmpty()) {
                return;
            }
            List<Long> userIds = notificationConfigService.listUserIdsByRoleCodes(roleCodes, escalation.getPlantCode());
            for (Long userId : userIds) {
                NotificationCreateDTO dto = new NotificationCreateDTO();
                dto.setUserId(userId);
                dto.setPlantCode(escalation.getPlantCode());
                dto.setType(scenarioCode);
                dto.setLevel("严重");
                dto.setTitle("供应商重复问题待升级审核");
                dto.setContent(escalation.getEscalationReason() + "；建议措施：" + escalation.getEscalationAction());
                dto.setBusinessType("ESCALATION");
                dto.setBusinessId(escalation.getId());
                dto.setCreatedBy(loginUser.getRealName());
                notificationService.createNotification(dto);
            }
        } catch (Exception e) {
            log.warn("发送升级通知失败：escalationId={}", escalation.getId(), e);
        }
    }

    private void sendExceptionStatusChangedNotification(ExceptionOrder order, LoginUser loginUser, String newStatus) {
        try {
            if ("已闭环".equals(newStatus)) {
                String scenarioCode = NotificationTypeEnum.EXCEPTION_CLOSED.getCode();
                List<String> roleCodes = notificationConfigService.getReceivingRoleCodes(scenarioCode);
                if (!roleCodes.isEmpty()) {
                    List<Long> userIds = notificationConfigService.listUserIdsByRoleCodes(roleCodes, order.getPlantCode());
                    for (Long userId : userIds) {
                        if (userId.equals(loginUser.getUserId())) {
                            continue;
                        }
                        NotificationCreateDTO managerNotification = NotificationTemplateHelper.forExceptionClosedToManager(
                                userId, order.getPlantCode(), order, loginUser.getRealName());
                        managerNotification.setType(scenarioCode);
                        notificationService.createNotification(managerNotification);
                    }
                }
            }
            NotificationCreateDTO dto = NotificationTemplateHelper.forExceptionStatusChanged(
                    loginUser.getUserId(), order.getPlantCode(), order, newStatus, loginUser.getRealName());
            notificationService.createNotification(dto);
        } catch (Exception e) {
            log.warn("发送异常单状态变更通知失败：exceptionId={}", order.getId(), e);
        }
    }

    /**
     * 配置驱动的通知发送：通过 notification_config 表读取接收角色 → 解析为 userId → 逐个发送站内信。
     * 管理员可通过 PUT /api/v1/admin/notification-config/{id} 随时调整场景开关与接收角色。
     */
    private void notifyByConfig(ExceptionOrder order, String scenarioCode, String title, String content) {
        try {
            List<String> roleCodes = notificationConfigService.getReceivingRoleCodes(scenarioCode);
            if (roleCodes.isEmpty()) {
                return; // 管理员禁用了此场景
            }
            List<Long> userIds = notificationConfigService.listUserIdsByRoleCodes(roleCodes, order.getPlantCode());
            if (userIds.isEmpty()) {
                return;
            }
            String operator = ExceptionModuleHelper.currentOperator();
            for (Long uid : userIds) {
                NotificationCreateDTO dto = NotificationTemplateHelper.forExceptionStatusChanged(
                        uid, order.getPlantCode(), order, "配置通知", operator);
                dto.setType(scenarioCode);
                dto.setTitle(title);
                dto.setContent(content);
                notificationService.createNotification(dto);
            }
        } catch (Exception e) {
            log.warn("配置驱动通知发送失败：exceptionId={}, scenarioCode={}", order.getId(), scenarioCode, e);
        }
    }

    /**
     * 生成异常单号：EX-YYYYMMDD-NNN。
     */
    private String generateExceptionNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LambdaQueryWrapper<ExceptionOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(ExceptionOrder::getExceptionNo, "EX-" + datePart + "-")
                .orderByDesc(ExceptionOrder::getExceptionNo)
                .last("LIMIT 1");
        ExceptionOrder latest = exceptionOrderMapper.selectOne(wrapper);
        int seq = 1;
        if (latest != null) {
            String no = latest.getExceptionNo();
            String seqStr = no.substring(no.lastIndexOf('-') + 1);
            seq = Integer.parseInt(seqStr) + 1;
        }
        return String.format("EX-%s-%03d", datePart, seq);
    }

    private LoginUser getCurrentLoginUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
