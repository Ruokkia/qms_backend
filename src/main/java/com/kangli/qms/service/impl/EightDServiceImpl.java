package com.kangli.qms.service.impl;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.dto.EightDSaveDTO;
import com.kangli.qms.entity.Exception8d;
import com.kangli.qms.entity.ExceptionOrder;
import com.kangli.qms.mapper.Exception8dMapper;
import com.kangli.qms.mapper.ExceptionOrderMapper;
import com.kangli.qms.service.EightDService;
import com.kangli.qms.vo.EightDVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    private static final List<String> STEPS = Arrays.asList("D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8");

    public EightDServiceImpl(Exception8dMapper exception8dMapper, ExceptionOrderMapper exceptionOrderMapper) {
        this.exception8dMapper = exception8dMapper;
        this.exceptionOrderMapper = exceptionOrderMapper;
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
        int currentStepIndex = STEPS.indexOf(dto.getCurrentStep());
        if (currentStepIndex < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "无效的 8D 步骤：" + dto.getCurrentStep());
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

        // 首次保存或更新到任意步骤，将 capaStatus 更新为进行中
        ExceptionOrder update = new ExceptionOrder();
        update.setId(exceptionId);
        update.setCapaStatus("进行中");
        exceptionOrderMapper.updateById(update);

        // 若当前为 D8 且异常单已闭环，则 capaStatus 改为已完成
        if ("D8".equals(dto.getCurrentStep()) && "已闭环".equals(order.getStatus())) {
            update.setCapaStatus("已完成");
            exceptionOrderMapper.updateById(update);
        }

        log.info("8D 报告已保存：exceptionId={}, currentStep={}", exceptionId, dto.getCurrentStep());
        EightDVO vo = new EightDVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    @Override
    @Transactional
    public EightDVO nextStep(Long exceptionId) {
        Exception8d record = exception8dMapper.selectByExceptionId(exceptionId);
        if (record == null) {
            // 降级：可能是 saveOrUpdate 恢复已删除记录时 @TableLogic 问题导致未查到，
            // 尝试从已删除记录中恢复（与 saveOrUpdate 保持一致）。
            record = exception8dMapper.selectByExceptionIdIgnoreDeleted(exceptionId);
            if (record == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "8D 报告不存在：" + exceptionId);
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

        // D8 到达后自动将 capaStatus 设置为已完成
        if ("D8".equals(nextStep)) {
            ExceptionOrder order = exceptionOrderMapper.selectById(exceptionId);
            if (order != null && !"已完成".equals(order.getCapaStatus()) && !"已闭环".equals(order.getStatus())) {
                ExceptionOrder update = new ExceptionOrder();
                update.setId(exceptionId);
                update.setCapaStatus("已完成");
                exceptionOrderMapper.updateById(update);
                log.info("8D 已到达 D8，capaStatus 自动流转为已完成：exceptionId={}", exceptionId);
            }
        }

        result.setExceptionNo(exceptionOrderMapper.selectById(exceptionId).getExceptionNo());
        return result;
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
            default: return null;
        }
    }
}
