package com.kangli.qms.service.exception.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.exception.entity.Exception8d;
import com.kangli.qms.domain.exception.entity.Exception8dStepLog;
import com.kangli.qms.domain.exception.entity.ExceptionApprovalConfig;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.mapper.Exception8dMapper;
import com.kangli.qms.domain.exception.mapper.Exception8dStepLogMapper;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.vo.EightDStepLogVO;
import com.kangli.qms.domain.exception.vo.EightDVO;
import com.kangli.qms.service.exception.EightDService;
import com.kangli.qms.service.exception.ExceptionApprovalConfigService;
import com.kangli.qms.service.exception.dto.EightDD1ReviewDTO;
import com.kangli.qms.service.exception.dto.EightDD1TeamDTO;
import com.kangli.qms.service.exception.dto.EightDSaveDTO;
import com.kangli.qms.service.exception.dto.StageApprovalDTO;
import com.kangli.qms.service.notification.NotificationConfigService;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.service.notification.enums.NotificationTypeEnum;
import com.kangli.qms.service.notification.helper.NotificationTemplateHelper;
import com.kangli.qms.service.exception.ExceptionConstants;
import com.kangli.qms.service.exception.ExceptionModuleHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 8D 报告服务实现。
 */
@Slf4j
@Service
public class EightDServiceImpl implements EightDService {

    private final Exception8dMapper exception8dMapper;
    private final ExceptionOrderMapper exceptionOrderMapper;
    private final Exception8dStepLogMapper stepLogMapper;
    private final ExceptionApprovalConfigService approvalConfigService;
    private final NotificationService notificationService;
    private final NotificationConfigService notificationConfigService;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    private static final String BUSINESS_TYPE_EXCEPTION = "EXCEPTION_ORDER";

    public EightDServiceImpl(Exception8dMapper exception8dMapper, ExceptionOrderMapper exceptionOrderMapper,
                             Exception8dStepLogMapper stepLogMapper,
                             ExceptionApprovalConfigService approvalConfigService,
                             NotificationService notificationService,
                             NotificationConfigService notificationConfigService,
                             SysUserMapper sysUserMapper,
                             ObjectMapper objectMapper) {
        this.exception8dMapper = exception8dMapper;
        this.exceptionOrderMapper = exceptionOrderMapper;
        this.stepLogMapper = stepLogMapper;
        this.approvalConfigService = approvalConfigService;
        this.notificationService = notificationService;
        this.notificationConfigService = notificationConfigService;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public EightDVO getByExceptionId(Long exceptionId) {
        Exception8d record = exception8dMapper.selectByExceptionId(exceptionId);
        if (record == null) {
            return null;
        }
        EightDVO vo = new EightDVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    @Override
    @Transactional
    public EightDVO saveOrUpdate(Long exceptionId, EightDSaveDTO dto) {
        ExceptionOrder order = exceptionOrderMapper.selectById(exceptionId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + exceptionId);
        }

        Exception8d existing = exception8dMapper.selectByExceptionId(exceptionId);

        // 版本号并发校验：若前端传了 version 且与 DB 不一致，拒绝保存
        if (dto.getVersion() != null && existing != null
                && !dto.getVersion().equals(existing.getVersion())) {
            log.warn("8D 版本冲突：exceptionId={}, clientVersion={}, dbVersion={}",
                    exceptionId, dto.getVersion(), existing.getVersion());
            throw new BusinessException(ResultCode.VERSION_CONFLICT);
        }

        int currentStepIndex = ExceptionConstants.EIGHT_D_STEP_ORDER.indexOf(dto.getCurrentStep());
        if (currentStepIndex < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的 8D 步骤：" + dto.getCurrentStep());
        }

        // 存在有效在档记录时，禁止回退或向前跳步，确保 8D 报告逐步、单向推进：
        // 仅允许「同步骤编辑」或「严格 +1 推进」。避免 PUT 绕过 nextStep 直接篡改/跳步。
        if (existing != null) {
            int existingStepIndex = ExceptionConstants.EIGHT_D_STEP_ORDER.indexOf(existing.getCurrentStep());
            if (existingStepIndex >= 0) {
                if (currentStepIndex < existingStepIndex) {
                    log.warn("8D 步骤回退被拒绝：exceptionId={}, from={}, to={}",
                            exceptionId, existing.getCurrentStep(), dto.getCurrentStep());
                    throw new BusinessException(ResultCode.EIGHT_D_STEP_INVALID);
                }
                if (currentStepIndex > existingStepIndex + 1) {
                    log.warn("8D 步骤跳步被拒绝：exceptionId={}, from={}, to={}",
                            exceptionId, existing.getCurrentStep(), dto.getCurrentStep());
                    throw new BusinessException(ResultCode.EIGHT_D_STEP_JUMP);
                }
            }
        }

        // 若正常查询未找到（可能因重置后被软删除），尝试从已删除记录中恢复
        if (existing == null) {
            existing = exception8dMapper.selectByExceptionIdIgnoreDeleted(exceptionId);
            if (existing != null) {
                // 恢复旧记录：重置到 D1，清空 D1~D8 字段，保留发起阶段已填的 D0
                existing.setCurrentStep("D1");
                existing.setD1Team(null);
                existing.setD1Members(null);
                existing.setD2ProblemDesc(null);
                existing.setD3Containment(null);
                existing.setD4RootCause(null);
                existing.setD5Corrective(null);
                existing.setD6Implementation(null);
                existing.setD7Preventive(null);
                existing.setD8Closure(null);
            }
        }

        Exception8d record = existing != null ? existing : new Exception8d();
        BeanUtils.copyProperties(dto, record);
        record.setExceptionId(exceptionId);
        record.setPlantCode(order.getPlantCode());
        record.setPlantName(order.getPlantName());

        if (existing == null) {
            // 全新记录 → 插入（此时 exception_id 唯一索引 + WHERE is_deleted=0 确保不冲突）
            record.setStepStatus("DRAFT");
            exception8dMapper.insert(record);
        } else {
            // 已有记录（含被软删除后恢复的场景）
            if (existing.getIsDeleted() != null && existing.getIsDeleted() == 1) {
                // 穿透 @TableLogic 恢复已删除记录：
                // updateById 会被 @TableLogic 自动注入 is_deleted=0 WHERE，
                // 而 DB 中该记录 is_deleted=1，导致匹配不到。必须先用原生 SQL 翻转标志位。
                exception8dMapper.restoreDeletedById(existing.getId());
                record.setIsDeleted((short) 0);
            }
            exception8dMapper.updateById(record);
        }
        // refetch 以同步 @Version 乐观锁版本号：updateById 后 DB 版本已 +1，内存对象 version 已过时
        record = exception8dMapper.selectByExceptionId(exceptionId);

        // 8D 进度与异常单 CAPA 状态联动：saveOrUpdate 不再提前将 capaStatus 置为「已完成」，
        // 避免与 status 状态机脱节；capaStatus=已完成 由 close（统一闭环）负责写入。
        ExceptionOrder update = new ExceptionOrder();
        update.setId(exceptionId);
        update.setCapaStatus("进行中");
        exceptionOrderMapper.updateById(update);

        // 留痕：记录本次保存的步骤内容快照（SAVE）
        String operator = ExceptionModuleHelper.currentOperator();
        writeStepLog(exceptionId, order.getPlantCode(), order.getPlantName(), dto.getCurrentStep(),
                getStepFieldContent(record, dto.getCurrentStep()), "SAVE", operator);

        log.info("8D 报告已保存：exceptionId={}, currentStep={}, operator={}",
                exceptionId, dto.getCurrentStep(), operator);
        EightDVO vo = new EightDVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    @Override
    @Transactional
    public EightDVO nextStep(Long exceptionId) {
        ExceptionModuleHelper.assertRole("R03", "R04", "R06");

        EightDStepContext ctx = resolveAndValidateNextStep(exceptionId);
        ExceptionOrder order = exceptionOrderMapper.selectById(exceptionId);

        // CAPA-8D 交错推进门禁（仅 BOTH 模式专用；纯 8D 不进 CAPA 门禁）
        if (ExceptionModuleHelper.isBothMode(order.getProcessType())) {
            checkCapaPhaseGate(order, ctx.currentStep, ctx.nextStep);
        }

        // 阶段审批拦截：配置了审批的阶段提交后 → PENDING_APPROVAL，等待审批通过后前进
        if (stageNeedApproval(order, ctx.currentStep)) {
            return enterApprovalAndReturn(exceptionId, ctx, order);
        }

        // 无审批配置：直接推进到下一阶段
        return advanceToNextStep(exceptionId, ctx, order);
    }

    // ---- nextStep 辅助方法 ----

    /** 解析当前 8D 记录并校验步骤可推进性 */
    private EightDStepContext resolveAndValidateNextStep(Long exceptionId) {
        Exception8d record = resolve8DRecord(exceptionId);
        validateStepContent(record);
        validateStepAdvanceable(record);
        int currentIndex = ExceptionConstants.EIGHT_D_STEP_ORDER.indexOf(record.getCurrentStep());
        String nextStep = ExceptionConstants.EIGHT_D_STEP_ORDER.get(currentIndex + 1);
        return new EightDStepContext(record, record.getCurrentStep(), nextStep, currentIndex);
    }

    /** 解析 8D 记录（含自动创建与软删除恢复） */
    private Exception8d resolve8DRecord(Long exceptionId) {
        Exception8d record = exception8dMapper.selectByExceptionId(exceptionId);
        if (record != null) {
            return record;
        }
        // 降级：从软删除记录中恢复
        record = exception8dMapper.selectByExceptionIdIgnoreDeleted(exceptionId);
        if (record == null) {
            return createDefault8DRecord(exceptionId);
        }
        // 恢复已软删除记录
        exception8dMapper.restoreDeletedById(record.getId());
        record.setIsDeleted((short) 0);
        record.setCurrentStep(ExceptionConstants.D1);
        clearAllStepFields(record);
        return record;
    }

    /** 创建默认 D1 8D 记录（历史脏数据 / 入口遗漏安全网） */
    private Exception8d createDefault8DRecord(Long exceptionId) {
        ExceptionOrder order = exceptionOrderMapper.selectById(exceptionId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + exceptionId);
        }
        Exception8d record = new Exception8d();
        record.setExceptionId(exceptionId);
        record.setCurrentStep(ExceptionConstants.D1);
        record.setPlantCode(order.getPlantCode());
        record.setPlantName(order.getPlantName());
        record.setCreatedBy(order.getCreatedBy());
        record.setUpdatedBy(order.getCreatedBy());
        exception8dMapper.insert(record);
        return record;
    }

    /** 清理所有步骤字段 */
    private void clearAllStepFields(Exception8d record) {
        record.setD1Team(null);
        record.setD1Members(null);
        record.setD2ProblemDesc(null);
        record.setD3Containment(null);
        record.setD4RootCause(null);
        record.setD5Corrective(null);
        record.setD6Implementation(null);
        record.setD7Preventive(null);
        record.setD8Closure(null);
    }

    /** 校验当前步骤内容已填写 */
    private void validateStepContent(Exception8d record) {
        String content = getStepFieldContent(record, record.getCurrentStep());
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "请先填写 " + record.getCurrentStep() + " 内容后再提交到下一步");
        }
    }

    /** 校验步骤可推进（有效步骤、非末尾、D1 特殊拦截） */
    private void validateStepAdvanceable(Exception8d record) {
        int idx = ExceptionConstants.EIGHT_D_STEP_ORDER.indexOf(record.getCurrentStep());
        if (idx < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的 8D 步骤：" + record.getCurrentStep());
        }
        if (idx >= ExceptionConstants.EIGHT_D_STEP_ORDER.size() - 1) {
            throw new BusinessException(ResultCode.EIGHT_D_ALREADY_CLOSED, "已到达 8D 最后一步");
        }
        if (ExceptionConstants.D1.equals(record.getCurrentStep())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "D1 阶段请通过团队提交与审核流程推进到 D2，不可直接使用「下一步」");
        }
    }

    /** 进入审批状态并返回 */
    private EightDVO enterApprovalAndReturn(Long exceptionId, EightDStepContext ctx, ExceptionOrder order) {
        Exception8d upd = new Exception8d();
        upd.setId(ctx.record.getId());
        upd.setStepStatus(ExceptionConstants.STATUS_PENDING_APPROVAL);
        exception8dMapper.updateById(upd);

        String operator = ExceptionModuleHelper.currentOperator();
        writeStepLog(exceptionId, order.getPlantCode(), order.getPlantName(), ctx.currentStep,
                getStepFieldContent(ctx.record, ctx.currentStep), "NEXT_STEP", operator);
        stepLogMapper.markPendingApproval(exceptionId, ctx.currentStep);

        log.info("8D 阶段 {} 提交后进入待审：exceptionId={}, approverRole={}",
                ctx.currentStep, exceptionId, approverRoleOf(order, ctx.currentStep));

        Exception8d latest = exception8dMapper.selectByExceptionId(exceptionId);
        EightDVO vo = new EightDVO();
        BeanUtils.copyProperties(latest, vo);
        vo.setExceptionNo(order.getExceptionNo());
        return vo;
    }

    /** 无审批时直接推进到下一阶段 */
    private EightDVO advanceToNextStep(Long exceptionId, EightDStepContext ctx, ExceptionOrder order) {
        EightDSaveDTO dto = new EightDSaveDTO();
        dto.setCurrentStep(ctx.nextStep);
        copyRecordFieldsToDTO(ctx.record, dto);

        EightDVO result = saveOrUpdate(exceptionId, dto);
        String operator = ExceptionModuleHelper.currentOperator();
        writeStepLog(exceptionId, order.getPlantCode(), order.getPlantName(), ctx.nextStep,
                getStepFieldContent(result, ctx.nextStep), "NEXT_STEP", operator);

        log.info("8D 已推进到 {}：exceptionId={}, operator={}", ctx.nextStep, exceptionId, operator);
        result.setExceptionNo(order.getExceptionNo());

        Exception8d latest = exception8dMapper.selectByExceptionId(exceptionId);
        if (latest != null) {
            result.setStepStatus(latest.getStepStatus());
            result.setVersion(latest.getVersion());
        }
        return result;
    }

    /** 将 8D 记录的步骤字段复制到 DTO */
    private void copyRecordFieldsToDTO(Exception8d record, EightDSaveDTO dto) {
        dto.setD1Team(record.getD1Team());
        dto.setD2ProblemDesc(record.getD2ProblemDesc());
        dto.setD3Containment(record.getD3Containment());
        dto.setD4RootCause(record.getD4RootCause());
        dto.setD5Corrective(record.getD5Corrective());
        dto.setD6Implementation(record.getD6Implementation());
        dto.setD7Preventive(record.getD7Preventive());
        dto.setD8Closure(record.getD8Closure());
    }

    /** nextStep 方法的步骤上下文 */
    private static class EightDStepContext {
        final Exception8d record;
        final String currentStep;
        final String nextStep;
        final int currentIndex;

        EightDStepContext(Exception8d record, String currentStep, String nextStep, int currentIndex) {
            this.record = record;
            this.currentStep = currentStep;
            this.nextStep = nextStep;
            this.currentIndex = currentIndex;
        }
    }

    @Override
    public List<EightDStepLogVO> getStepLogs(Long exceptionId) {
        List<Exception8dStepLog> logs = stepLogMapper.selectByExceptionId(exceptionId);
        List<EightDStepLogVO> vos = new ArrayList<>();
        for (Exception8dStepLog log : logs) {
            EightDStepLogVO vo = new EightDStepLogVO();
            BeanUtils.copyProperties(log, vo);
            vo.setOperationDesc("SAVE".equals(log.getOperation()) ? "保存内容" : "推进到下一步");
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 获取指定步骤对应的 8D 字段内容，用于校验步骤是否已填写。
     */
    private String getStepFieldContent(Exception8d record, String step) {
        switch (step) {
            case "D0": return record.getD0Symptom() != null ? record.getD0Symptom() : "D0";
            case "D1": return record.getD1Members() != null ? record.getD1Members() : record.getD1Team();
            case "D2": return record.getD2ProblemDesc();
            case "D3": return record.getD3Containment();
            case "D4": return record.getD4RootCause();
            case "D5": return record.getD5Corrective();
            case "D6": return record.getD6Implementation();
            case "D7": return record.getD7Preventive();
            case "D8": return record.getD8Closure();
            default: return null;
        }
    }

    /**
     * 写入一条 8D 步骤留痕记录。
     */
    private void writeStepLog(Long exceptionId, String plantCode, String plantName,
                              String step, String content, String operation, String operator) {
        Exception8dStepLog log = new Exception8dStepLog();
        log.setExceptionId(exceptionId);
        log.setStep(step);
        log.setStepContent(content);
        log.setOperation(operation);
        log.setOperator(operator);
        log.setOperatedAt(LocalDateTime.now());
        log.setPlantCode(plantCode);
        log.setPlantName(plantName);
        stepLogMapper.insert(log);
    }

    @Override
    @Transactional
    public EightDVO approveStage(Long exceptionId, StageApprovalDTO dto) {
        ExceptionApprovalConfig config = approvalConfigService.resolveConfig(dto.getProcessFlow(), dto.getStage(), ExceptionModuleHelper.currentPlantCode());
        if (config == null || config.getNeedApproval() == null || config.getNeedApproval() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, dto.getStage() + " 未配置审批，无需审批");
        }
        ExceptionModuleHelper.requireApprovalPrivilege(config.getApproverRole());
        Exception8d record = exception8dMapper.selectByExceptionId(exceptionId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "8D 记录不存在：" + exceptionId);
        }
        if (!"PENDING_APPROVAL".equals(record.getStepStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前阶段不处于待审状态，无法审批");
        }
        String operator = ExceptionModuleHelper.currentOperator();
        ExceptionOrder order = exceptionOrderMapper.selectById(exceptionId);
        // 留痕审批结果
        writeApprovalLog(exceptionId, order.getPlantCode(), order.getPlantName(), dto.getStage(),
                "APPROVED", operator, dto.getComment());
        // 审批通过：解除待审，并自动前进到下一阶段（DRAFT，由用户继续填写）
        int idx = ExceptionConstants.EIGHT_D_STEP_ORDER.indexOf(dto.getStage());
        if (idx < 0 || idx >= ExceptionConstants.EIGHT_D_STEP_ORDER.size() - 1) {
            // 末阶段（如 D8）审批通过：置 APPROVED 待闭环（闭环由独立 close 流程校验前置条件后处理）
            record.setStepStatus("APPROVED");
            exception8dMapper.updateById(record);
            log.info("8D 末阶段 {} 审批通过，待闭环：exceptionId={}, approver={}", dto.getStage(), exceptionId, operator);
        } else {
            String nextStep = ExceptionConstants.EIGHT_D_STEP_ORDER.get(idx + 1);
            // CAPA-8D 交错推进门禁（仅 BOTH 模式专用；纯 8D 不进 CAPA 门禁）
            if (ExceptionModuleHelper.isBothMode(order.getProcessType())) {
                checkCapaPhaseGate(order, dto.getStage(), nextStep);
            }
            Exception8d upd = new Exception8d();
            upd.setId(record.getId());
            upd.setCurrentStep(nextStep);
            upd.setStepStatus("DRAFT");
            exception8dMapper.updateById(upd);
            writeStepLog(exceptionId, order.getPlantCode(), order.getPlantName(), nextStep,
                    getStepFieldContent(record, nextStep), "NEXT_STEP", operator);
            record.setCurrentStep(nextStep);
            record.setStepStatus("DRAFT");
            log.info("8D 阶段 {} 审批通过，自动推进到 {}：exceptionId={}, approver={}",
                    dto.getStage(), nextStep, exceptionId, operator);
        }
        // refetch 同步 @Version：updateById 后 DB version 已 +1，内存对象 version 已过时
        record = exception8dMapper.selectByExceptionId(exceptionId);
        EightDVO vo = new EightDVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    /** 阶段是否需要审批（按分公司 + 流程 + 阶段解析配置） */
    private boolean stageNeedApproval(ExceptionOrder order, String stage) {
        ExceptionApprovalConfig config =
                approvalConfigService.resolveConfig("8D", stage, order.getPlantCode());
        return config != null && config.getNeedApproval() != null && config.getNeedApproval() == 1;
    }

    /** 取阶段审批角色（仅用于日志） */
    private String approverRoleOf(ExceptionOrder order, String stage) {
        ExceptionApprovalConfig config =
                approvalConfigService.resolveConfig("8D", stage, order.getPlantCode());
        return config != null ? config.getApproverRole() : "";
    }

    @Override
    @Transactional
    public EightDVO rejectStage(Long exceptionId, StageApprovalDTO dto) {
        ExceptionApprovalConfig config = approvalConfigService.resolveConfig(dto.getProcessFlow(), dto.getStage(), ExceptionModuleHelper.currentPlantCode());
        if (config == null || config.getNeedApproval() == null || config.getNeedApproval() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, dto.getStage() + " 未配置审批，无需审批");
        }
        ExceptionModuleHelper.requireApprovalPrivilege(config.getApproverRole());
        Exception8d record = exception8dMapper.selectByExceptionId(exceptionId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "8D 记录不存在：" + exceptionId);
        }
        if (!"PENDING_APPROVAL".equals(record.getStepStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前阶段不处于待审状态，无法驳回");
        }
        // 唯一允许的回退例外：驳回到上一阶段重新填写
        String current = record.getCurrentStep();
        int idx = ExceptionConstants.EIGHT_D_STEP_ORDER.indexOf(current);
        if (idx <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已在首个阶段，无法回退");
        }
        String prevStep = ExceptionConstants.EIGHT_D_STEP_ORDER.get(idx - 1);
        record.setCurrentStep(prevStep);
        record.setStepStatus("REJECTED");
        exception8dMapper.updateById(record);
        // 留痕审批结果
        ExceptionOrder order = exceptionOrderMapper.selectById(exceptionId);
        writeApprovalLog(exceptionId, order.getPlantCode(), order.getPlantName(), current,
                "REJECTED", ExceptionModuleHelper.currentOperator(), dto.getComment());
        log.info("8D 阶段 {} 审批驳回，回退到 {}：exceptionId={}", current, prevStep, exceptionId);
        // refetch 同步 @Version：updateById 后 DB version 已 +1
        record = exception8dMapper.selectByExceptionId(exceptionId);
        EightDVO vo = new EightDVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    /**
     * 写入一条阶段审批留痕（APPROVED / REJECTED）。
     */
    private void writeApprovalLog(Long exceptionId, String plantCode, String plantName,
                                  String stage, String status, String approver, String comment) {
        Exception8dStepLog log = new Exception8dStepLog();
        log.setExceptionId(exceptionId);
        log.setStep(stage);
        log.setOperation("APPROVE");
        log.setOperator(approver);
        log.setOperatedAt(LocalDateTime.now());
        log.setPlantCode(plantCode);
        log.setPlantName(plantName);
        log.setApprovalStatus(status);
        log.setApprover(approver);
        log.setApprovalComment(comment);
        log.setApprovalTime(LocalDateTime.now());
        stepLogMapper.insert(log);
    }

    @Override
    @Transactional
    public EightDVO submitD1Team(Long exceptionId, EightDD1TeamDTO dto) {
        Exception8d record = exception8dMapper.selectByExceptionId(exceptionId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "8D 记录不存在：" + exceptionId);
        }
        if (!"D1".equals(record.getCurrentStep())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前阶段不是 D1，无法提交团队");
        }
        // 版本号乐观锁校验
        if (dto.getVersion() != null && !dto.getVersion().equals(record.getVersion())) {
            throw new BusinessException(ResultCode.VERSION_CONFLICT);
        }

        // 校验提交人为指定负责人
        LoginUser loginUser = LoginUserHolder.get();
        ExceptionOrder order = exceptionOrderMapper.selectById(exceptionId);
        if (order.getOwnerId() == null || !order.getOwnerId().equals(loginUser.getUserId())) {
            if (!"R00".equals(loginUser.getRoleCode())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "仅指定的整改负责人可组建团队");
            }
        }

        // 序列化成员列表为 JSON，同时写姓名逗号串到 d1Team 向后兼容
        try {
            String json = objectMapper.writeValueAsString(dto.getMemberList());
            record.setD1Members(json);
            List<String> names = dto.getMemberList().stream()
                    .map(EightDD1TeamDTO.D1MemberItem::getRealName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            record.setD1Team(String.join(", ", names));
        } catch (Exception e) {
            log.error("D1 团队成员序列化失败：exceptionId={}", exceptionId, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "团队成员序列化失败");
        }

        if (dto.getCapaOwner() != null) {
            record.setCapaOwner(dto.getCapaOwner());
        }

        // 提交后进入待审核团队状态
        record.setStepStatus("SUBMITTED");
        exception8dMapper.updateById(record);

        // 留痕
        String operator = ExceptionModuleHelper.currentOperator();
        writeStepLog(exceptionId, order.getPlantCode(), order.getPlantName(), "D1",
                record.getD1Members(), "D1_TEAM_SUBMIT", operator);

        // 通知质量角色审核
        notifyQualityForD1Review(exceptionId, order, loginUser);

        log.info("D1 团队已提交：exceptionId={}, members={}", exceptionId, record.getD1Team());
        // refetch 同步 @Version：updateById 后 DB version 已 +1
        record = exception8dMapper.selectByExceptionId(exceptionId);
        EightDVO vo = new EightDVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    @Override
    @Transactional
    public EightDVO reviewD1Team(Long exceptionId, EightDD1ReviewDTO dto) {
        Exception8d record = exception8dMapper.selectByExceptionId(exceptionId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "8D 记录不存在：" + exceptionId);
        }
        if (!"D1".equals(record.getCurrentStep())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前阶段不是 D1，无法审核团队");
        }
        if (!"SUBMITTED".equals(record.getStepStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "D1 团队尚未提交，无法审核");
        }
        // 版本号乐观锁校验
        if (!dto.getVersion().equals(record.getVersion())) {
            throw new BusinessException(ResultCode.VERSION_CONFLICT);
        }

        // 仅质量角色（R04/R06）可审核
        LoginUser loginUser = LoginUserHolder.get();
        if (!"R00".equals(loginUser.getRoleCode())
                && !"R04".equals(loginUser.getRoleCode())
                && !"R06".equals(loginUser.getRoleCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅质量部门可审核 D1 团队构成");
        }

        ExceptionOrder order = exceptionOrderMapper.selectById(exceptionId);
        String operator = ExceptionModuleHelper.currentOperator();

        if (Boolean.TRUE.equals(dto.getApproved())) {
            // 通过：团队生效，currentStep 推进至 D2
            record.setStepStatus("APPROVED");
            record.setCurrentStep("D2");
            exception8dMapper.updateById(record);

            writeStepLog(exceptionId, order.getPlantCode(), order.getPlantName(), "D1",
                    record.getD1Members(), "D1_TEAM_APPROVED", operator);
            writeApprovalLog(exceptionId, order.getPlantCode(), order.getPlantName(), "D1",
                    "APPROVED", operator, dto.getReviewComment());

            // 通知负责人及全体团队成员：团队已通过，进入 D2
            notifyD1TeamApproved(exceptionId, order, record);

            log.info("D1 团队审核通过：exceptionId={}", exceptionId);
        } else {
            // 驳回：退回 D1 由负责人重新组建
            if (dto.getReviewComment() == null || dto.getReviewComment().trim().isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "驳回时必须填写审核意见");
            }
            record.setStepStatus("REJECTED");
            exception8dMapper.updateById(record);

            writeApprovalLog(exceptionId, order.getPlantCode(), order.getPlantName(), "D1",
                    "REJECTED", operator, dto.getReviewComment());

            // 通知负责人重新组建
            notifyD1TeamRejected(exceptionId, order, record, dto.getReviewComment());

            log.info("D1 团队审核驳回：exceptionId={}, reason={}", exceptionId, dto.getReviewComment());
        }

        // refetch 同步 @Version：updateById 后 DB version 已 +1
        record = exception8dMapper.selectByExceptionId(exceptionId);
        EightDVO vo = new EightDVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    /**
     * 通知质量角色审核 D1 团队（从 NotificationConfig 读取接收角色）。
     */
    private void notifyQualityForD1Review(Long exceptionId, ExceptionOrder order, LoginUser submitter) {
        try {
            String scenarioCode = NotificationTypeEnum.EIGHT_D_TEAM_PENDING_REVIEW.getCode();
            List<String> roleCodes = notificationConfigService.getReceivingRoleCodes(scenarioCode);
            if (roleCodes.isEmpty()) {
                return;
            }
            List<Long> userIds = notificationConfigService.listUserIdsByRoleCodes(roleCodes, order.getPlantCode());
            for (Long uid : userIds) {
                NotificationCreateDTO n = NotificationTemplateHelper.for8DTeamPendingReview(
                        uid, order.getPlantCode(), order,
                        "负责人 " + submitter.getRealName() + " 已提交异常单 "
                                + order.getExceptionNo() + " 的 D1 团队，请审核。",
                        submitter.getRealName());
                n.setType(scenarioCode);
                notificationService.createNotification(n);
            }
        } catch (Exception e) {
            log.warn("发送 D1 团队审核通知失败：exceptionId={}", exceptionId, e);
        }
    }

    /**
     * 通知负责人及全体团队成员：D1 团队审核通过，进入 D2。
     */
    private void notifyD1TeamApproved(Long exceptionId, ExceptionOrder order, Exception8d record) {
        try {
            java.util.Set<Long> userIds = new java.util.LinkedHashSet<>();
            // 负责人
            if (order.getOwnerId() != null) {
                userIds.add(order.getOwnerId());
            }
            // 团队成员（从 d1Members JSON 解析）
            if (record.getD1Members() != null) {
                List<EightDD1TeamDTO.D1MemberItem> members = objectMapper.readValue(
                        record.getD1Members(), new TypeReference<List<EightDD1TeamDTO.D1MemberItem>>() {});
                for (EightDD1TeamDTO.D1MemberItem m : members) {
                    if (m.getUserId() != null) {
                        userIds.add(m.getUserId());
                    }
                }
            }
            for (Long uid : userIds) {
                NotificationCreateDTO n = NotificationTemplateHelper.for8DTeamApproved(
                        uid, order.getPlantCode(), order, ExceptionModuleHelper.currentOperator());
                notificationService.createNotification(n);
            }
        } catch (Exception e) {
            log.warn("发送 D1 团队通过通知失败：exceptionId={}", exceptionId, e);
        }
    }

    /**
     * 通知负责人：D1 团队审核未通过，需重新组建。
     */
    private void notifyD1TeamRejected(Long exceptionId, ExceptionOrder order, Exception8d record, String reason) {
        try {
            if (order.getOwnerId() == null) return;
            NotificationCreateDTO n = NotificationTemplateHelper.for8DTeamRejected(
                    order.getOwnerId(), order.getPlantCode(), order, ExceptionModuleHelper.currentOperator());
            n.setContent("异常单 " + order.getExceptionNo() + " 的 D1 团队未通过审核，原因：" + reason + "，请重新组建团队。");
            notificationService.createNotification(n);
        } catch (Exception e) {
            log.warn("发送 D1 团队驳回通知失败：exceptionId={}", exceptionId, e);
        }
    }

    /**
     * CAPA-8D 交错推进门禁：8D 提交到关键步骤前，强制校验 CAPA 相位是否已通过对应审批，
     * 未通过则拒绝推进（抛 BusinessException）。校验通过后再发送待审批通知。
     * <p>门禁规则（BOTH 模式）：</p>
     * <ul>
     *   <li>D4→D5：必须已通过 CAPA 根因审批（相位 = ROOT_CAUSE_APPROVED）</li>
     *   <li>D5→D6：必须已通过 CAPA 措施审批（相位 = MEASURES_APPROVED）</li>
     * </ul>
     */
    private void checkCapaPhaseGate(ExceptionOrder order, String currentStep, String nextStep) {
        if (ExceptionModuleHelper.isBothMode(order.getProcessType())) {
            // BOTH 模式：D4→D5 前置闸门，需 CAPA 根因审批通过
            if (ExceptionConstants.D4.equals(currentStep) && ExceptionConstants.D5.equals(nextStep)) {
                if (!ExceptionConstants.CAPA_PHASE_ROOT_CAUSE_APPROVED.equals(order.getCapaPhase())) {
                    log.warn("8D 推进被 CAPA 根因审批门禁拦截：exceptionId={}, currentStep={}, capaPhase={}",
                            order.getId(), currentStep, order.getCapaPhase());
                    throw new BusinessException(ResultCode.CAPA_PHASE_GATE_NOT_MET,
                            "D5 措施方案制定需先通过 CAPA 根因审批。当前 CAPA 相位："
                                    + (order.getCapaPhase() != null ? order.getCapaPhase() : "CAPA 立项"));
                }
                notifyByConfig(order, NotificationTypeEnum.EIGHT_D_D4_SUBMITTED.getCode(),
                        "D4 根因分析已提交",
                        "异常单【" + order.getExceptionNo() + "】D4 根因分析已提交，待 CAPA 根因审批。");
            }
            // BOTH 模式：D5→D6 前置闸门，需 CAPA 措施审批通过
            if (ExceptionConstants.D5.equals(currentStep) && ExceptionConstants.D6.equals(nextStep)) {
                if (!ExceptionConstants.CAPA_PHASE_MEASURES_APPROVED.equals(order.getCapaPhase())) {
                    log.warn("8D 推进被 CAPA 措施审批门禁拦截：exceptionId={}, currentStep={}, capaPhase={}",
                            order.getId(), currentStep, order.getCapaPhase());
                    throw new BusinessException(ResultCode.CAPA_PHASE_GATE_NOT_MET,
                            "D6 措施实施需先通过 CAPA 措施审批。当前 CAPA 相位："
                                    + (order.getCapaPhase() != null ? order.getCapaPhase() : "CAPA 立项"));
                }
                notifyByConfig(order, NotificationTypeEnum.EIGHT_D_D5_SUBMITTED.getCode(),
                        "D5 措施方案已提交",
                        "异常单【" + order.getExceptionNo() + "】D5 措施方案已提交，待 CAPA 措施审批。");
            }
        }
    }

    /** 更新异常单的 CAPA 相位并记录日志 */
    private void advanceCapaPhase(ExceptionOrder order, String targetPhase, String remark) {
        ExceptionOrder update = new ExceptionOrder();
        update.setId(order.getId());
        update.setCapaPhase(targetPhase);
        exceptionOrderMapper.updateById(update);
        log.info("CAPA 相位推进：exceptionId={}, {}→{}, remark={}",
                order.getId(), order.getCapaPhase(), targetPhase, remark);
        order.setCapaPhase(targetPhase);
    }

    /**
     * 配置驱动的通知发送：通过 notification_config 表读取接收角色 → 解析为 userId → 逐个发送站内信。
     * 管理员可通过 PUT /api/v1/admin/notification-config/{id} 随时调整场景开关与接收角色。
     */
    private void notifyByConfig(ExceptionOrder order, String scenarioCode, String title, String content) {
        try {
            List<String> roleCodes = notificationConfigService.getReceivingRoleCodes(scenarioCode);
            if (roleCodes.isEmpty()) {
                return;
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

}
