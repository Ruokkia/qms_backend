package com.kangli.qms.service.exception.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.PageResult;
import org.springframework.dao.DuplicateKeyException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.exception.dto.ExceptionCloseDTO;
import com.kangli.qms.service.exception.dto.ExceptionUpdateDTO;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.exception.entity.Exception8d;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.entity.Escalation;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
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
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.domain.exception.mapper.RectificationPlanMapper;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.exception.mapper.VerificationRecordMapper;
import com.kangli.qms.service.admin.AuditLogService;
import com.kangli.qms.service.exception.ExceptionService;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.domain.exception.vo.CloseReadinessVO;
import com.kangli.qms.domain.exception.vo.EightDVO;
import com.kangli.qms.domain.exception.vo.ExceptionAnalysisItemVO;
import com.kangli.qms.domain.exception.vo.ExceptionAnalysisVO;
import com.kangli.qms.domain.exception.vo.ExceptionDetailVO;
import com.kangli.qms.domain.exception.vo.ExceptionStatsVO;
import com.kangli.qms.domain.supplier.vo.SupplierExceptionSummaryVO;
import com.kangli.qms.domain.exception.vo.QualityExceptionDecisionVO;
import com.kangli.qms.domain.exception.vo.QualityRuleCatalogVO;
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
    private final SysUserMapper sysUserMapper;
    private final AuditLogMapper auditLogMapper;
    private final AuditLogService auditLogService;
    private final RectificationPlanMapper rectificationPlanMapper;
    private final QualityExceptionRuleEvaluator qualityRuleEvaluator;

    public ExceptionServiceImpl(ExceptionOrderMapper exceptionOrderMapper,
                                 ImprovementActionMapper improvementActionMapper,
                                 VerificationRecordMapper verificationRecordMapper,
                                 SupplierMapper supplierMapper,
                                 EscalationMapper escalationMapper,
                                 Exception8dMapper exception8dMapper,
                                 NotificationService notificationService,
                                 MaterialInspectionMapper materialInspectionMapper,
                                 FaiInspectionRecordMapper faiInspectionRecordMapper,
                                 SysUserMapper sysUserMapper,
                                 AuditLogMapper auditLogMapper,
                                 AuditLogService auditLogService,
                                 RectificationPlanMapper rectificationPlanMapper,
                                 QualityExceptionRuleEvaluator qualityRuleEvaluator) {
        this.exceptionOrderMapper = exceptionOrderMapper;
        this.improvementActionMapper = improvementActionMapper;
        this.verificationRecordMapper = verificationRecordMapper;
        this.supplierMapper = supplierMapper;
        this.escalationMapper = escalationMapper;
        this.exception8dMapper = exception8dMapper;
        this.notificationService = notificationService;
        this.materialInspectionMapper = materialInspectionMapper;
        this.faiInspectionRecordMapper = faiInspectionRecordMapper;
        this.sysUserMapper = sysUserMapper;
        this.auditLogMapper = auditLogMapper;
        this.auditLogService = auditLogService;
        this.rectificationPlanMapper = rectificationPlanMapper;
        this.qualityRuleEvaluator = qualityRuleEvaluator;
    }

    // ===== 分页查询 =====

    @Override
    public PageResult<ExceptionOrder> list(int page, int size, String severity, String status,
                                            Long supplierId, String sourceType,
                                            String processType, String capaStatus,
                                            String startDate, String endDate) {
        String plantCode = getCurrentPlantCode();
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

    /** 批量回填异常单列表的供应商名称（避免 N+1） */
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
                r.setSupplierName(nameMap.get(r.getSupplierId()));
            }
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

        // 关联来料检验记录
        if ("来料不良".equals(order.getSourceType()) && order.getSourceId() != null) {
            MaterialInspection mi = materialInspectionMapper.selectById(order.getSourceId());
            vo.setMaterialInspection(mi);
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

        // 若建单时直接携带 8D 流程且已进入「进行中」，同步自动建 D1 报告
        if (processIncludes8D(order.getProcessType()) && "进行中".equals(order.getCapaStatus())) {
            ensureEightDInitialized(order, loginUser);
        }
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
        order.setCapaStatus("进行中");
        order.setProcessType(decision.getProcessType());
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

        // 根据 supplierCode 查找 supplierId
        if (StringUtils.hasText(inspection.getSupplierCode())) {
            LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Supplier::getSupplierCode, inspection.getSupplierCode())
                    .eq(Supplier::getPlantCode, plantCode);
            Supplier supplier = supplierMapper.selectOne(wrapper);
            if (supplier != null) {
                order.setSupplierId(supplier.getId());
            }
        }

        insertExceptionWithRetry(order);

        if ("8D".equals(decision.getProcessType()) || "BOTH".equals(decision.getProcessType())) {
            initializeEightD(order, loginUser);
        }

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
                .eq(ExceptionOrder::getSourceType, "首件不合格")
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
        order.setSourceType("首件不合格");
        order.setSourceId(record.getId());
        order.setPlantCode(plantCode);
        order.setPlantName(StringUtils.hasText(record.getPlantName())
                ? record.getPlantName() : loginUser.getPlantCode().getChineseName());
        order.setStatus("待整改");
        order.setCapaStatus("待发起");
        order.setSeverity(decision.getSeverity());
        order.setProcessType(decision.getProcessType());
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

        if ("8D".equals(decision.getProcessType()) || "BOTH".equals(decision.getProcessType())) {
            initializeEightD(order, loginUser);
        }
        sendExceptionCreatedNotifications(order, loginUser);
        auditLogService.record(TABLE_NAME_EXCEPTION, order.getId(), "CREATE", null, order,
                "首件不合格自动触发；" + decision.getRuleReason());

        log.info("首件不合格自动建异常单：faiId={}, exceptionNo={}, severity={}, processType={}",
                record.getId(), order.getExceptionNo(), order.getSeverity(), order.getProcessType());
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

    private static final List<String> VALID_PROCESS_TYPES = List.of("CAPA", "8D", "BOTH");

    @Override
    @Transactional
    public ExceptionOrder initiate(Long id, String processType) {
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

        ExceptionOrder update = new ExceptionOrder();
        update.setId(id);
        update.setProcessType(processType);
        update.setCapaStatus("进行中");
        LoginUser loginUser = getCurrentLoginUser();
        update.setUpdatedBy(loginUser.getRealName());
        exceptionOrderMapper.updateById(update);

        // 8D 流程：发起即自动建 D1 报告，避免后续 next-step 查不到记录 404
        existing.setProcessType(processType);
        ensureEightDInitialized(existing, loginUser);

        // 审计：记录发起整改流程操作
        auditLogService.record(TABLE_NAME_EXCEPTION, id, ACTION_UPDATE, existing, update, "发起整改流程：" + processType);

        log.info("发起整改流程：exceptionId={}, processType={}", id, processType);
        return exceptionOrderMapper.selectById(id);
    }

    // ===== 闭环 =====

    @Override
    @Transactional
    public void close(Long id, ExceptionCloseDTO dto) {
        ExceptionOrder order = exceptionOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + id);
        }
        if ("已闭环".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "异常单已闭环，无需重复操作");
        }

        // 增强前置条件检查，拼装缺失项
        List<String> missing = new ArrayList<>();

        // 1. 流程必须已发起
        if (order.getProcessType() == null || "待发起".equals(order.getCapaStatus())) {
            missing.add("整改流程尚未发起");
        }

        // 2. 所有整改计划必须完成
        LambdaQueryWrapper<RectificationPlan> planWrapper = new LambdaQueryWrapper<>();
        planWrapper.eq(RectificationPlan::getExceptionId, id);
        List<RectificationPlan> plans = rectificationPlanMapper.selectList(planWrapper);
        long planTotal = plans.size();
        long planDone = plans.stream().filter(p -> "已完成".equals(p.getStatus())).count();
        if (planTotal > 0 && planDone < planTotal) {
            missing.add("整改计划未全部完成（" + planDone + "/" + planTotal + "）");
        }

        // 3. 所有改善措施必须完成
        LambdaQueryWrapper<ImprovementAction> actionWrapper = new LambdaQueryWrapper<>();
        actionWrapper.eq(ImprovementAction::getExceptionId, id);
        List<ImprovementAction> actions = improvementActionMapper.selectList(actionWrapper);
        long actionTotal = actions.size();
        long actionDone = actions.stream().filter(a -> "DONE".equals(a.getStatus())).count();
        if (actionTotal == 0) {
            missing.add("尚无改善措施");
        } else if (actionDone < actionTotal) {
            missing.add("存在未完成的改善措施（" + actionDone + "/" + actionTotal + " DONE）");
        }

        // 4. 最新验证记录必须通过
        LambdaQueryWrapper<VerificationRecord> verifyWrapper = new LambdaQueryWrapper<>();
        verifyWrapper.eq(VerificationRecord::getExceptionId, id)
                .orderByDesc(VerificationRecord::getVerifyDate)
                .orderByDesc(VerificationRecord::getId);
        List<VerificationRecord> verifs = verificationRecordMapper.selectList(verifyWrapper);
        if (verifs.isEmpty()) {
            missing.add("尚无验证记录");
        } else if (!"通过".equals(verifs.get(0).getResult())) {
            missing.add("最新验证结果不是「通过」");
        }

        // 5. 若流程含 8D：必须 D8 完成
        if (processIncludes8D(order.getProcessType())) {
            Exception8d eightD = exception8dMapper.selectByExceptionId(id);
            if (eightD == null) {
                missing.add("8D 报告未创建");
            } else {
                if (!"D8".equals(eightD.getCurrentStep())) {
                    missing.add("8D 报告未走完（当前：" + eightD.getCurrentStep() + "）");
                }
                // 检查 D1~D8 是否全部已填写
                List<String> unfilledDSteps = new ArrayList<>();
                if (eightD.getD1Team() == null || eightD.getD1Team().trim().isEmpty()) unfilledDSteps.add("D1");
                if (eightD.getD2ProblemDesc() == null || eightD.getD2ProblemDesc().trim().isEmpty()) unfilledDSteps.add("D2");
                if (eightD.getD3Containment() == null || eightD.getD3Containment().trim().isEmpty()) unfilledDSteps.add("D3");
                if (eightD.getD4RootCause() == null || eightD.getD4RootCause().trim().isEmpty()) unfilledDSteps.add("D4");
                if (eightD.getD5Corrective() == null || eightD.getD5Corrective().trim().isEmpty()) unfilledDSteps.add("D5");
                if (eightD.getD6Implementation() == null || eightD.getD6Implementation().trim().isEmpty()) unfilledDSteps.add("D6");
                if (eightD.getD7Preventive() == null || eightD.getD7Preventive().trim().isEmpty()) unfilledDSteps.add("D7");
                if (eightD.getD8Closure() == null || eightD.getD8Closure().trim().isEmpty()) unfilledDSteps.add("D8");
                if (!unfilledDSteps.isEmpty()) {
                    missing.add("8D 步骤未填写：" + String.join("、", unfilledDSteps));
                }
            }
        }

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
        update.setRemark(order.getRemark() != null ? order.getRemark() + "\n闭环原因：" + dto.getCloseReason() : "闭环原因：" + dto.getCloseReason());
        LoginUser loginUser = getCurrentLoginUser();
        update.setUpdatedBy(loginUser.getRealName());

        exceptionOrderMapper.updateById(update);

        // 审计：记录线上闭环操作
        auditLogService.record(TABLE_NAME_EXCEPTION, id, ACTION_UPDATE, order, update, "线上闭环：" + dto.getCloseReason());

        // 发送状态变更通知
        sendExceptionStatusChangedNotification(order, loginUser, "已闭环");

        log.info("异常单 {} 已闭环，操作人：{}", id, loginUser.getRealName());
    }

    // ===== 闭环前置条件检查 =====

    @Override
    public CloseReadinessVO closeReadiness(Long id) {
        ExceptionOrder order = exceptionOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + id);
        }

        List<CloseReadinessVO.CheckItem> checks = new ArrayList<>();
        boolean canClose = true;

        // 1. 整改流程是否已发起
        {
            CloseReadinessVO.CheckItem item = new CloseReadinessVO.CheckItem();
            item.setItem("整改流程");
            if (order.getProcessType() != null && !"待发起".equals(order.getCapaStatus())) {
                item.setStatus("PASS");
                item.setDetail("已发起（" + order.getProcessType() + "，" + order.getCapaStatus() + "）");
            } else {
                item.setStatus("FAIL");
                item.setDetail("尚未发起整改流程");
                canClose = false;
            }
            checks.add(item);
        }

        // 2. 整改计划（CAPA/BOTH 需要）
        {
            CloseReadinessVO.CheckItem item = new CloseReadinessVO.CheckItem();
            item.setItem("整改计划");
            LambdaQueryWrapper<RectificationPlan> planWrapper = new LambdaQueryWrapper<>();
            planWrapper.eq(RectificationPlan::getExceptionId, id);
            List<RectificationPlan> plans = rectificationPlanMapper.selectList(planWrapper);
            long planTotal = plans.size();
            long planDone = plans.stream().filter(p -> "已完成".equals(p.getStatus())).count();
            boolean capaOrBoth = order.getProcessType() != null
                    && (order.getProcessType().equals("CAPA") || order.getProcessType().equals("BOTH"));
            if (!capaOrBoth && planTotal == 0) {
                item.setStatus("NA");
                item.setDetail("纯8D模式，不需要整改计划");
            } else if (planTotal == 0) {
                item.setStatus("FAIL");
                item.setDetail("尚未制定整改计划");
                canClose = false;
            } else {
                if (planDone >= planTotal) {
                    item.setStatus("PASS");
                } else {
                    item.setStatus("FAIL");
                    canClose = false;
                }
                item.setDetail(planDone + "/" + planTotal + " 已完成");
            }
            checks.add(item);
        }

        // 3. 改善措施
        {
            CloseReadinessVO.CheckItem item = new CloseReadinessVO.CheckItem();
            item.setItem("改善措施");
            LambdaQueryWrapper<ImprovementAction> actionWrapper = new LambdaQueryWrapper<>();
            actionWrapper.eq(ImprovementAction::getExceptionId, id);
            List<ImprovementAction> actions = improvementActionMapper.selectList(actionWrapper);
            long actionTotal = actions.size();
            long actionDone = actions.stream().filter(a -> "DONE".equals(a.getStatus())).count();
            if (actionTotal == 0) {
                item.setStatus("FAIL");
                item.setDetail("尚无改善措施");
                canClose = false;
            } else if (actionDone >= actionTotal) {
                item.setStatus("PASS");
                item.setDetail(actionDone + "/" + actionTotal + " DONE");
            } else {
                item.setStatus("FAIL");
                item.setDetail(actionDone + "/" + actionTotal + " DONE，" + (actionTotal - actionDone) + "条PENDING");
                canClose = false;
            }
            checks.add(item);
        }

        // 4. 验证记录
        {
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
                canClose = false;
            } else if ("通过".equals(verifs.get(0).getResult())) {
                item.setStatus("PASS");
                item.setDetail(verifs.size() + "条验证，最新结果「通过」");
            } else {
                item.setStatus("FAIL");
                item.setDetail(verifs.size() + "条验证，最新结果「不通过」");
                canClose = false;
            }
            checks.add(item);
        }

        // 5. 8D 报告
        {
            CloseReadinessVO.CheckItem item = new CloseReadinessVO.CheckItem();
            item.setItem("8D报告");
            if (order.getProcessType() == null || !processIncludes8D(order.getProcessType())) {
                item.setStatus("NA");
                item.setDetail("未启用8D流程");
            } else {
                Exception8d eightD = exception8dMapper.selectByExceptionId(id);
                if (eightD == null) {
                    item.setStatus("FAIL");
                    item.setDetail("8D 报告未创建");
                    canClose = false;
                } else if (!"D8".equals(eightD.getCurrentStep())) {
                    item.setStatus("FAIL");
                    item.setDetail("当前步骤：" + eightD.getCurrentStep() + "，需走完 D8");
                    canClose = false;
                } else {
                    // D8 且检查所有步骤是否填写
                    List<String> unfilledDSteps = new ArrayList<>();
                    if (eightD.getD1Team() == null || eightD.getD1Team().trim().isEmpty()) unfilledDSteps.add("D1");
                    if (eightD.getD2ProblemDesc() == null || eightD.getD2ProblemDesc().trim().isEmpty()) unfilledDSteps.add("D2");
                    if (eightD.getD3Containment() == null || eightD.getD3Containment().trim().isEmpty()) unfilledDSteps.add("D3");
                    if (eightD.getD4RootCause() == null || eightD.getD4RootCause().trim().isEmpty()) unfilledDSteps.add("D4");
                    if (eightD.getD5Corrective() == null || eightD.getD5Corrective().trim().isEmpty()) unfilledDSteps.add("D5");
                    if (eightD.getD6Implementation() == null || eightD.getD6Implementation().trim().isEmpty()) unfilledDSteps.add("D6");
                    if (eightD.getD7Preventive() == null || eightD.getD7Preventive().trim().isEmpty()) unfilledDSteps.add("D7");
                    if (eightD.getD8Closure() == null || eightD.getD8Closure().trim().isEmpty()) unfilledDSteps.add("D8");
                    if (!unfilledDSteps.isEmpty()) {
                        item.setStatus("FAIL");
                        item.setDetail("D8已到达，但以下步骤未填写：" + String.join("、", unfilledDSteps));
                        canClose = false;
                    } else {
                        item.setStatus("PASS");
                        item.setDetail("8D D1~D8 全部完成");
                    }
                }
            }
            checks.add(item);
        }

        CloseReadinessVO result = new CloseReadinessVO();
        result.setCanClose(canClose);
        result.setChecks(checks);
        return result;
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
                .set(ExceptionOrder::getClosedAt, null)
                .set(ExceptionOrder::getUpdatedBy, loginUser.getRealName()));

        // 审计
        auditLogService.record(TABLE_NAME_EXCEPTION, id, ACTION_UPDATE, order,
                "status=待整改 capaStatus=待发起 processType=NULL closedAt=NULL", "重置为最初状态（手动测试）");

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
        String plantCode = getCurrentPlantCode();
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
        String plantCode = getCurrentPlantCode();
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
        String plantCode = getCurrentPlantCode();
        List<SupplierExceptionSummaryVO> list = exceptionOrderMapper.selectSupplierSummary(
                plantCode, supplierId, startDate, endDate, minCount != null ? minCount : 1);

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
            List<String> roleCodes = new ArrayList<>(Arrays.asList("R02", "R05"));
            if ("严重".equals(order.getSeverity())) {
                roleCodes.add("R06");
                roleCodes.add("R07");
            }
            List<SysUser> recipients = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPlantCode, order.getPlantCode())
                    .eq(SysUser::getStatus, (short) 1)
                    .in(SysUser::getRoleCode, roleCodes));

            List<Long> recipientIds = recipients.stream()
                    .map(SysUser::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toCollection(ArrayList::new));
            if (loginUser.getUserId() != null && !recipientIds.contains(loginUser.getUserId())) {
                recipientIds.add(loginUser.getUserId());
            }

            for (Long userId : recipientIds) {
                NotificationCreateDTO dto = new NotificationCreateDTO();
                dto.setUserId(userId);
                dto.setType("EXCEPTION_CREATED");
                dto.setLevel(order.getNotificationLevel());
                dto.setTitle(("严重".equals(order.getSeverity()) ? "【严重】" : "")
                        + "新异常单 " + order.getExceptionNo());
                dto.setContent("判定：" + order.getRuleReason()
                        + "；系统已发起" + order.getProcessType()
                        + "；整改截止：" + order.getDeadline());
                dto.setBusinessType(BUSINESS_TYPE_EXCEPTION);
                dto.setBusinessId(order.getId());
                dto.setPlantCode(order.getPlantCode());
                dto.setCreatedBy(loginUser.getRealName());
                notificationService.createNotification(dto);
            }
        } catch (Exception e) {
            log.warn("发送异常单创建通知失败：exceptionId={}", order.getId(), e);
        }
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

    private void initializeEightD(ExceptionOrder order, LoginUser loginUser) {
        Exception8d eightD = new Exception8d();
        eightD.setExceptionId(order.getId());
        eightD.setCurrentStep("D1");
        eightD.setPlantCode(order.getPlantCode());
        eightD.setPlantName(order.getPlantName());
        eightD.setCreatedBy(loginUser.getRealName());
        eightD.setUpdatedBy(loginUser.getRealName());
        exception8dMapper.insert(eightD);
    }

    /**
     * 幂等确保 8D 报告存在：仅当 processType 含 8D 时生效。
     * ① 已存在则跳过；② 已软删除则恢复为 D1（避免 exception_id 唯一索引冲突）；
     * ③ 不存在则新建 D1。供发起流程 / 建单等多入口安全复用。
     */
    private void ensureEightDInitialized(ExceptionOrder order, LoginUser loginUser) {
        if (!processIncludes8D(order.getProcessType())) {
            return;
        }
        Exception8d existing = exception8dMapper.selectByExceptionId(order.getId());
        if (existing != null) {
            return; // 已存在，跳过
        }
        Exception8d deleted = exception8dMapper.selectByExceptionIdIgnoreDeleted(order.getId());
        if (deleted != null) {
            // 已软删除则恢复为初始 D1 状态
            exception8dMapper.restoreDeletedById(deleted.getId());
            deleted.setCurrentStep("D1");
            deleted.setD1Team(null);
            deleted.setD2ProblemDesc(null);
            deleted.setD3Containment(null);
            deleted.setD4RootCause(null);
            deleted.setD5Corrective(null);
            deleted.setD6Implementation(null);
            deleted.setD7Preventive(null);
            deleted.setD8Closure(null);
            deleted.setIsDeleted((short) 0);
            deleted.setUpdatedBy(loginUser.getRealName());
            exception8dMapper.updateById(deleted);
            return;
        }
        initializeEightD(order, loginUser);
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
        List<SysUser> recipients = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPlantCode, escalation.getPlantCode())
                .eq(SysUser::getStatus, (short) 1)
                .in(SysUser::getRoleCode, Arrays.asList("R05", "R06", "R07")));
        for (SysUser user : recipients) {
            NotificationCreateDTO dto = new NotificationCreateDTO();
            dto.setUserId(user.getId());
            dto.setType("ESCALATION_TRIGGERED");
            dto.setLevel("严重");
            dto.setTitle("供应商重复问题待升级审核");
            dto.setContent(escalation.getEscalationReason() + "；建议措施：" + escalation.getEscalationAction());
            dto.setBusinessType("ESCALATION");
            dto.setBusinessId(escalation.getId());
            dto.setPlantCode(escalation.getPlantCode());
            dto.setCreatedBy(loginUser.getRealName());
            notificationService.createNotification(dto);
        }
    }

    private void sendExceptionStatusChangedNotification(ExceptionOrder order, LoginUser loginUser, String newStatus) {
        try {
            if ("\u5df2\u95ed\u73af".equals(newStatus)) {
                List<SysUser> managers = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPlantCode, order.getPlantCode())
                        .eq(SysUser::getStatus, (short) 1)
                        .eq(SysUser::getRoleCode, "R06"));
                for (SysUser manager : managers) {
                    if (manager.getId().equals(loginUser.getUserId())) {
                        continue;
                    }
                    NotificationCreateDTO managerNotification = new NotificationCreateDTO();
                    managerNotification.setUserId(manager.getId());
                    managerNotification.setType("EXCEPTION_CLOSED");
                    managerNotification.setLevel("\u63d0\u9192");
                    managerNotification.setTitle("\u5f02\u5e38\u5355" + order.getExceptionNo() + " \u5df2\u95ed\u73af");
                    managerNotification.setContent("\u5f02\u5e38\u5355\u5df2\u5b8c\u6210\u95ed\u73af\uff0c\u64cd\u4f5c\u4eba\uff1a" + loginUser.getRealName());
                    managerNotification.setBusinessType(BUSINESS_TYPE_EXCEPTION);
                    managerNotification.setBusinessId(order.getId());
                    managerNotification.setPlantCode(order.getPlantCode());
                    managerNotification.setCreatedBy(loginUser.getRealName());
                    notificationService.createNotification(managerNotification);
                }
            }
            NotificationCreateDTO dto = new NotificationCreateDTO();
            dto.setUserId(loginUser.getUserId());
            dto.setType("EXCEPTION_STATUS_CHANGED");
            dto.setTitle("异常单 " + order.getExceptionNo() + " 状态变更");
            dto.setContent("新状态：" + newStatus);
            dto.setBusinessType(BUSINESS_TYPE_EXCEPTION);
            dto.setBusinessId(order.getId());
            dto.setPlantCode(order.getPlantCode());
            dto.setCreatedBy(loginUser.getRealName());
            notificationService.createNotification(dto);
        } catch (Exception e) {
            log.warn("发送异常单状态变更通知失败：exceptionId={}", order.getId(), e);
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

    private String getCurrentPlantCode() {
        LoginUser loginUser = getCurrentLoginUser();
        return loginUser.getPlantCode().name();
    }

    private LoginUser getCurrentLoginUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }

    /**
     * 判断流程类型是否包含 8D
     */
    private boolean processIncludes8D(String processType) {
        return "8D".equals(processType) || "BOTH".equals(processType);
    }
}
