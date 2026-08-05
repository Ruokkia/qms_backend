package com.kangli.qms.service.exception.impl;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.exception.dto.EightDSaveDTO;
import com.kangli.qms.domain.exception.entity.Exception8d;
import com.kangli.qms.domain.exception.entity.Exception8dStepLog;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.mapper.Exception8dMapper;
import com.kangli.qms.domain.exception.mapper.Exception8dStepLogMapper;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.service.exception.EightDService;
import com.kangli.qms.domain.exception.vo.EightDStepLogVO;
import com.kangli.qms.domain.exception.vo.EightDVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 8D 报告服务实现。
 */
@Slf4j
@Service
public class EightDServiceImpl implements EightDService {

    private final Exception8dMapper exception8dMapper;
    private final ExceptionOrderMapper exceptionOrderMapper;
    private final Exception8dStepLogMapper stepLogMapper;

    private static final List<String> STEPS = Arrays.asList("D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8");

    public EightDServiceImpl(Exception8dMapper exception8dMapper, ExceptionOrderMapper exceptionOrderMapper,
                             Exception8dStepLogMapper stepLogMapper) {
        this.exception8dMapper = exception8dMapper;
        this.exceptionOrderMapper = exceptionOrderMapper;
        this.stepLogMapper = stepLogMapper;
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

        int currentStepIndex = STEPS.indexOf(dto.getCurrentStep());
        if (currentStepIndex < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的 8D 步骤：" + dto.getCurrentStep());
        }

        // 存在有效在档记录时，禁止回退或向前跳步，确保 8D 报告逐步、单向推进：
        // 仅允许「同步骤编辑」或「严格 +1 推进」。避免 PUT 绕过 nextStep 直接篡改/跳步。
        if (existing != null) {
            int existingStepIndex = STEPS.indexOf(existing.getCurrentStep());
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
                // 恢复旧记录：重置步骤到 D1，清空所有 Dx 字段，避免"不可回退"误报
                existing.setCurrentStep("D1");
                existing.setD1Team(null);
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

        // 8D 进度与异常单 CAPA 状态联动：saveOrUpdate 不再提前将 capaStatus 置为「已完成」，
        // 避免与 status 状态机脱节；capaStatus=已完成 由 close（统一闭环）负责写入。
        ExceptionOrder update = new ExceptionOrder();
        update.setId(exceptionId);
        update.setCapaStatus("进行中");
        exceptionOrderMapper.updateById(update);

        // 留痕：记录本次保存的步骤内容快照（SAVE）
        String operator = currentOperator();
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
        // 权限加固：推进 8D 步骤需 R03/R04/R06（R00 超级管理员绕过）
        assertRole("R03", "R04", "R06");
        Exception8d record = exception8dMapper.selectByExceptionId(exceptionId);
        if (record == null) {
            // 降级：可能是 saveOrUpdate 恢复已删除记录时 @TableLogic 问题导致未查到，
            // 尝试从已删除记录中恢复（与 saveOrUpdate 保持一致）。
            record = exception8dMapper.selectByExceptionIdIgnoreDeleted(exceptionId);
            if (record == null) {
                // 安全网：8D 流程已发起但未建记录（历史脏数据 / 入口遗漏），
                // 自动建 D1 并直接返回，不前进、不校验 D1 内容，消除 404。
                ExceptionOrder order = exceptionOrderMapper.selectById(exceptionId);
                if (order == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "异常单不存在：" + exceptionId);
                }
                record = new Exception8d();
                record.setExceptionId(exceptionId);
                record.setCurrentStep("D1");
                record.setPlantCode(order.getPlantCode());
                record.setPlantName(order.getPlantName());
                record.setCreatedBy(order.getCreatedBy());
                record.setUpdatedBy(order.getCreatedBy());
                exception8dMapper.insert(record);
                EightDVO vo = new EightDVO();
                BeanUtils.copyProperties(record, vo);
                vo.setExceptionNo(order.getExceptionNo());
                return vo;
            }
            // 恢复已软删除记录：先穿透 @TableLogic 翻转标志位
            exception8dMapper.restoreDeletedById(record.getId());
            record.setIsDeleted((short) 0);
            // 重置到 D1（恢复场景下步骤应从头开始）
            record.setCurrentStep("D1");
            record.setD1Team(null);
            record.setD2ProblemDesc(null);
            record.setD3Containment(null);
            record.setD4RootCause(null);
            record.setD5Corrective(null);
            record.setD6Implementation(null);
            record.setD7Preventive(null);
            record.setD8Closure(null);
        }
        // 检查当前步骤内容是否已填写，防止空内容直接跳过
        String currentStepContent = getStepFieldContent(record, record.getCurrentStep());
        if (currentStepContent == null || currentStepContent.trim().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先填写 " + record.getCurrentStep() + " 内容后再提交到下一步");
        }
        int currentIndex = STEPS.indexOf(record.getCurrentStep());
        if (currentIndex < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的 8D 步骤：" + record.getCurrentStep());
        }
        if (currentIndex >= STEPS.size() - 1) {
            throw new BusinessException(ResultCode.EIGHT_D_ALREADY_CLOSED, "已到达 8D 最后一步");
        }
        String nextStep = STEPS.get(currentIndex + 1);

        EightDSaveDTO dto = new EightDSaveDTO();
        dto.setCurrentStep(nextStep);
        dto.setD1Team(record.getD1Team());
        dto.setD2ProblemDesc(record.getD2ProblemDesc());
        dto.setD3Containment(record.getD3Containment());
        dto.setD4RootCause(record.getD4RootCause());
        dto.setD5Corrective(record.getD5Corrective());
        dto.setD6Implementation(record.getD6Implementation());
        dto.setD7Preventive(record.getD7Preventive());
        dto.setD8Closure(record.getD8Closure());

        EightDVO result = saveOrUpdate(exceptionId, dto);

        // 留痕：记录本次推进操作（NEXT_STEP），内容为到达步骤的快照
        ExceptionOrder order = exceptionOrderMapper.selectById(exceptionId);
        String operator = currentOperator();
        writeStepLog(exceptionId, order.getPlantCode(), order.getPlantName(), nextStep,
                getStepFieldContent(result, nextStep), "NEXT_STEP", operator);

        log.info("8D 已推进到 {}：exceptionId={}, operator={}", nextStep, exceptionId, operator);
        result.setExceptionNo(order.getExceptionNo());
        return result;
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
            case "D1": return record.getD1Team();
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

    /**
     * 获取当前操作人真实姓名；未登录兜底为「系统」。
     */
    private String currentOperator() {
        try {
            return LoginUserHolder.get().getRealName();
        } catch (Exception e) {
            return "系统";
        }
    }

    /**
     * 角色权限校验：R00 超级管理员绕过；其余角色仅允许在 allowedRoles 内的操作。
     */
    private void assertRole(String... allowedRoles) {
        try {
            LoginUser loginUser = LoginUserHolder.get();
            if (loginUser == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
            }
            if ("R00".equals(loginUser.getRoleCode())) {
                return;
            }
            for (String role : allowedRoles) {
                if (role.equals(loginUser.getRoleCode())) {
                    return;
                }
            }
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "当前角色（" + loginUser.getRoleCode() + "）无权执行该操作");
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
    }
}
