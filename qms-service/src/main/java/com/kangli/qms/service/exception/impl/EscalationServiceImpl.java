package com.kangli.qms.service.exception.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.service.exception.dto.EscalationCheckDTO;
import com.kangli.qms.service.exception.dto.EscalationCreateDTO;
import com.kangli.qms.service.exception.dto.EscalationReviewDTO;
import com.kangli.qms.service.exception.dto.EscalationPlanDTO;
import com.kangli.qms.service.exception.dto.EscalationExecutionDTO;
import com.kangli.qms.service.exception.dto.EscalationVerificationDTO;
import com.kangli.qms.service.exception.dto.EscalationCloseDTO;
import com.kangli.qms.domain.exception.entity.Escalation;
import com.kangli.qms.domain.supplier.entity.Supplier;
import com.kangli.qms.domain.exception.mapper.EscalationMapper;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.service.exception.EscalationService;
import com.kangli.qms.service.exception.EscalationWorkflowPolicy;
import com.kangli.qms.domain.exception.vo.EscalationCheckResultVO;
import com.kangli.qms.domain.exception.vo.TriggeredSupplierVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

/**
 * 升级 Service 实现 — 含批量升级检查（90天内同类不良≥N次自动触发）。
 */
@Slf4j
@Service
public class EscalationServiceImpl extends ServiceImpl<EscalationMapper, Escalation> implements EscalationService {

    private final ExceptionOrderMapper exceptionOrderMapper;
    private final SupplierMapper supplierMapper;

    public EscalationServiceImpl(ExceptionOrderMapper exceptionOrderMapper, SupplierMapper supplierMapper) {
        this.exceptionOrderMapper = exceptionOrderMapper;
        this.supplierMapper = supplierMapper;
    }

    @Override
    @Transactional
    public Escalation create(EscalationCreateDTO dto) {
        LoginUser loginUser = getCurrentLoginUser();
        Supplier supplier = supplierMapper.selectById(dto.getSupplierId());
        if (supplier == null || (supplier.getIsDeleted() != null && supplier.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "供应商不存在或已删除");
        }
        if (!loginUser.getPlantCode().name().equals(supplier.getPlantCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能向其他分公司的供应商发起升级");
        }
        if (!"启用".equals(supplier.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已停用的供应商不能发起升级");
        }

        Escalation escalation = new Escalation();
        escalation.setSupplierId(String.valueOf(supplier.getId()));
        escalation.setSupplierCode(supplier.getSupplierCode());
        escalation.setSupplierName(supplier.getSupplierName());
        escalation.setMaterialCode(dto.getMaterialCode());
        escalation.setEscalationReason(dto.getEscalationReason().trim());
        escalation.setEscalationAction(dto.getEscalationAction().trim());
        escalation.setRelatedExceptionIds(dto.getRelatedExceptionIds());
        escalation.setRemark(dto.getRemark());
        escalation.setStatus("PENDING_REVIEW");
        escalation.setProcessStage("PENDING_REVIEW");
        escalation.setPlantCode(loginUser.getPlantCode().name());
        escalation.setPlantName(loginUser.getPlantCode().getChineseName());
        escalation.setCreatedBy(loginUser.getRealName());
        escalation.setUpdatedBy(loginUser.getRealName());
        save(escalation);
        return escalation;
    }

    @Override
    public EscalationCheckResultVO checkEscalation(EscalationCheckDTO dto) {
        LoginUser loginUser = getCurrentLoginUser();
        String plantCode = loginUser.getPlantCode().name();

        int daysWindow = dto != null && dto.getDaysWindow() != null ? dto.getDaysWindow() : 90;
        int minRepeatCount = dto != null && dto.getMinRepeatCount() != null ? dto.getMinRepeatCount() : 3;

        List<TriggeredSupplierVO> triggered = exceptionOrderMapper.selectRepeatExceptions(
                plantCode, daysWindow, minRepeatCount);

        // 升级任务必须能够落到供应商主数据；未映射的脏来料不能作为升级候选。
        triggered = triggered.stream()
                .filter(candidate -> candidate.getSupplierId() != null)
                .collect(Collectors.toList());

        // 解析逗号分隔的异常单 ID
        for (TriggeredSupplierVO t : triggered) {
            t.setWindowDays(daysWindow);
            t.setShouldEscalate(true);
            if (t.getRelatedExceptionIdsStr() != null && !t.getRelatedExceptionIdsStr().isEmpty()) {
                t.setRelatedExceptionIds(
                        Arrays.stream(t.getRelatedExceptionIdsStr().split(","))
                                .map(String::trim)
                                .map(Long::parseLong)
                                .collect(Collectors.toList())
                );
            } else {
                t.setRelatedExceptionIds(new ArrayList<>());
            }
        }

        // 如果指定了供应商 ID，过滤
        if (dto != null && dto.getSupplierId() != null) {
            triggered = triggered.stream()
                    .filter(t -> dto.getSupplierId().equals(t.getSupplierId()))
                    .collect(Collectors.toList());
        }

        EscalationCheckResultVO result = new EscalationCheckResultVO();
        result.setTotalChecked(triggered.size());
        result.setTriggeredSuppliers(triggered);

        log.info("升级检查完成：检查到 {} 个触发升级的供应商", triggered.size());
        return result;
    }

    @Override
    @Transactional
    public Escalation review(Long id, EscalationReviewDTO dto) {
        LoginUser loginUser = getCurrentLoginUser();
        Escalation escalation = getById(id);
        if (escalation == null || !loginUser.getPlantCode().name().equals(escalation.getPlantCode())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "升级任务不存在");
        }
        if (!"PENDING_REVIEW".equals(escalation.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅待审核升级任务可执行审核");
        }
        if (!"APPROVE".equals(dto.getDecision()) && !"REJECT".equals(dto.getDecision())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审核决定必须是 APPROVE 或 REJECT");
        }

        escalation.setStatus("APPROVE".equals(dto.getDecision()) ? "ACTIVE" : "REJECTED");
        escalation.setProcessStage("APPROVE".equals(dto.getDecision()) ? EscalationWorkflowPolicy.PLAN : EscalationWorkflowPolicy.REJECTED);
        escalation.setReviewOpinion(dto.getOpinion());
        escalation.setReviewedBy(loginUser.getRealName());
        escalation.setReviewedAt(LocalDateTime.now());
        escalation.setSignatureUser(loginUser.getRealName());
        escalation.setSignatureTime(LocalDateTime.now());
        escalation.setSignatureReason("供应商重复问题升级审核");
        escalation.setUpdatedBy(loginUser.getRealName());
        updateById(escalation);
        return escalation;
    }

    @Override
    @Transactional
    public Escalation savePlan(Long id, EscalationPlanDTO dto) {
        Escalation escalation = requireEscalation(id);
        EscalationWorkflowPolicy.requireStage(escalation.getProcessStage(), EscalationWorkflowPolicy.PLAN, "制定升级措施");
        escalation.setActionPlan(dto.getActionPlan());
        escalation.setOwnerName(dto.getOwnerName());
        escalation.setPlanFilledBy(getCurrentLoginUser().getRealName());
        escalation.setDueDate(dto.getDueDate());
        escalation.setProcessStage(EscalationWorkflowPolicy.EXECUTION);
        touch(escalation);
        return escalation;
    }

    @Override
    @Transactional
    public Escalation submitExecution(Long id, EscalationExecutionDTO dto) {
        Escalation escalation = requireEscalation(id);
        EscalationWorkflowPolicy.requireStage(escalation.getProcessStage(), EscalationWorkflowPolicy.EXECUTION, "提交执行跟踪");
        LoginUser user = getCurrentLoginUser();
        escalation.setExecutionRecord(dto.getExecutionRecord());
        escalation.setExecutedBy(user.getRealName());
        escalation.setExecutedAt(LocalDateTime.now());
        escalation.setProcessStage(EscalationWorkflowPolicy.VERIFICATION);
        touch(escalation);
        return escalation;
    }

    @Override
    @Transactional
    public Escalation verify(Long id, EscalationVerificationDTO dto) {
        Escalation escalation = requireEscalation(id);
        EscalationWorkflowPolicy.requireStage(escalation.getProcessStage(), EscalationWorkflowPolicy.VERIFICATION, "提交效果验证");
        if (!"PASS".equals(dto.getResult()) && !"FAIL".equals(dto.getResult())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "验证结论必须为 PASS 或 FAIL");
        }
        LoginUser user = getCurrentLoginUser();
        escalation.setVerificationResult(dto.getResult());
        escalation.setVerificationEvidence(dto.getEvidence());
        escalation.setVerifiedBy(user.getRealName());
        escalation.setVerifiedAt(LocalDateTime.now());
        escalation.setProcessStage("PASS".equals(dto.getResult()) ? EscalationWorkflowPolicy.PENDING_CLOSE_APPROVAL : EscalationWorkflowPolicy.PLAN);
        touch(escalation);
        return escalation;
    }

    @Override
    @Transactional
    public Escalation close(Long id, EscalationCloseDTO dto) {
        Escalation escalation = requireEscalation(id);
        EscalationWorkflowPolicy.requireStage(escalation.getProcessStage(), EscalationWorkflowPolicy.PENDING_CLOSE_APPROVAL, "关闭审批");
        LoginUser user = getCurrentLoginUser();
        escalation.setStatus("CLOSED");
        escalation.setProcessStage(EscalationWorkflowPolicy.CLOSED);
        escalation.setCloseReason(dto.getReason());
        escalation.setClosedBy(user.getRealName());
        escalation.setClosedAt(LocalDateTime.now());
        escalation.setSignatureUser(user.getRealName());
        escalation.setSignatureTime(LocalDateTime.now());
        escalation.setSignatureReason("供应商升级关闭审批");
        touch(escalation);
        return escalation;
    }

    private Escalation requireEscalation(Long id) {
        LoginUser user = getCurrentLoginUser();
        Escalation escalation = getById(id);
        if (escalation == null || !user.getPlantCode().name().equals(escalation.getPlantCode())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "升级任务不存在");
        }
        return escalation;
    }

    private void touch(Escalation escalation) {
        escalation.setUpdatedBy(getCurrentLoginUser().getRealName());
        updateById(escalation);
    }

    private LoginUser getCurrentLoginUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null || loginUser.getPlantCode() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息");
        }
        return loginUser;
    }
}
