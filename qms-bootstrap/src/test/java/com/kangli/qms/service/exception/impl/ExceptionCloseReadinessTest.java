package com.kangli.qms.service.exception.impl;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.exception.entity.Exception8d;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.entity.ImprovementAction;
import com.kangli.qms.domain.exception.entity.RectificationPlan;
import com.kangli.qms.domain.exception.entity.VerificationRecord;
import com.kangli.qms.domain.exception.mapper.Exception8dMapper;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.mapper.ImprovementActionMapper;
import com.kangli.qms.domain.exception.mapper.RectificationPlanMapper;
import com.kangli.qms.domain.exception.mapper.VerificationRecordMapper;
import com.kangli.qms.domain.exception.vo.CloseReadinessVO;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.service.exception.ExceptionConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * P0 缺口补测：M2 异常单闭环前置条件。
 *
 * <p>覆盖维度（对应 test-plan §10.2 / 闭环前置）：源码 evaluateCloseChecks 的 5 项检查，
 * 全部采用「正确行为」断言（原 test-plan 标注的锁现状项源码已实现，转回归守护）：
 * <ul>
 *   <li>整改流程：processType 已设且 capaStatus≠待发起 → PASS。</li>
 *   <li>整改计划：CAPA/BOTH 模式需全部「已完成」。</li>
 *   <li>改善措施：全部 ACTION_STATUS_DONE → PASS。</li>
 *   <li>验证记录：最新一条 result=通过 且 verifierName 非空 且时序合规 → PASS。</li>
 *   <li>8D 报告：含 8D 且 stepStatus=终审通过 → PASS；未终审通过 → FAIL。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("M2 异常单闭环前置条件")
class ExceptionCloseReadinessTest {

    @Mock
    private ExceptionOrderMapper exceptionOrderMapper;
    @Mock
    private RectificationPlanMapper rectificationPlanMapper;
    @Mock
    private ImprovementActionMapper improvementActionMapper;
    @Mock
    private VerificationRecordMapper verificationRecordMapper;
    @Mock
    private Exception8dMapper exception8dMapper;

    private ExceptionServiceImpl exceptionService;

    @BeforeEach
    void setUp() {
        LoginUserHolder.set(LoginUser.builder()
                .userId(1L).account("tester").plantCode(PlantCode.SZ).build());
        // 仅注入 closeReadiness 路径依赖的 5 个 mapper，其余字段保持默认 mock。
        exceptionService = new ExceptionServiceImpl(
                exceptionOrderMapper, improvementActionMapper, verificationRecordMapper,
                null, null, exception8dMapper, null,
                null, null, null, null, null, null, rectificationPlanMapper,
                null, null, null);
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private ExceptionOrder baseOrder(String processType) {
        ExceptionOrder o = new ExceptionOrder();
        o.setId(100L);
        o.setExceptionNo("EX-100");
        o.setProcessType(processType);
        o.setCapaStatus("整改中"); // 非「待发起」→ 整改流程 PASS
        o.setPlantCode("SZ");
        return o;
    }

    private RectificationPlan donePlan() {
        RectificationPlan p = new RectificationPlan();
        p.setId(1L);
        p.setStatus("已完成");
        return p;
    }

    private ImprovementAction doneAction() {
        ImprovementAction a = new ImprovementAction();
        a.setId(1L);
        a.setStatus(ExceptionConstants.ACTION_STATUS_DONE);
        a.setCompletedAt(java.time.LocalDateTime.now());
        return a;
    }

    private VerificationRecord passedVerify() {
        VerificationRecord v = new VerificationRecord();
        v.setId(1L);
        v.setResult("通过");
        v.setVerifierName("张三");
        v.setVerifyDate(LocalDate.now());
        return v;
    }

    private Exception8d completed8d() {
        Exception8d d8 = new Exception8d();
        d8.setCurrentStep(ExceptionConstants.D8); // 已走完 D8
        d8.setD1Members("成员");
        d8.setD1Team("团队");
        d8.setD2ProblemDesc("问题描述");
        d8.setD3Containment("围堵");
        d8.setD4RootCause("根因");
        d8.setD5Corrective("纠正");
        d8.setD6Implementation("实施");
        d8.setD7Preventive("预防");
        d8.setD8Closure("闭环");
        return d8;
    }

    @Test
    @DisplayName("纯 CAPA：整改计划已完成 + 改善措施完成 + 验证通过 → 可闭环")
    void pureCapa_allDone_canClose() {
        ExceptionOrder o = baseOrder(ExceptionConstants.PROCESS_CAPA);
        when(exceptionOrderMapper.selectById(100L)).thenReturn(o);
        when(rectificationPlanMapper.selectList(any())).thenReturn(List.of(donePlan()));
        when(improvementActionMapper.selectList(any())).thenReturn(List.of(doneAction()));
        when(verificationRecordMapper.selectList(any())).thenReturn(List.of(passedVerify()));
        when(exception8dMapper.selectByExceptionId(anyLong())).thenReturn(null);

        CloseReadinessVO vo = exceptionService.closeReadiness(100L);

        assertThat(vo.isCanClose()).isTrue();
        assertThat(vo.getChecks()).allMatch(c -> "PASS".equals(c.getStatus()) || "NA".equals(c.getStatus()));
    }

    @Test
    @DisplayName("含 8D：8D 未走完 D8 → 不可闭环")
    void with8d_8dNotClosed_cannotClose() {
        ExceptionOrder o = baseOrder(ExceptionConstants.PROCESS_BOTH);
        when(exceptionOrderMapper.selectById(100L)).thenReturn(o);
        when(rectificationPlanMapper.selectList(any())).thenReturn(List.of(donePlan()));
        when(improvementActionMapper.selectList(any())).thenReturn(List.of(doneAction()));
        when(verificationRecordMapper.selectList(any())).thenReturn(List.of(passedVerify()));
        Exception8d d8 = new Exception8d();
        d8.setCurrentStep("D1"); // 当前步骤仅为 D1，未走完 D8
        when(exception8dMapper.selectByExceptionId(anyLong())).thenReturn(d8);

        CloseReadinessVO vo = exceptionService.closeReadiness(100L);

        assertThat(vo.isCanClose()).isFalse();
        assertThat(vo.getChecks()).anyMatch(c -> "8D报告".equals(c.getItem()) && "FAIL".equals(c.getStatus()));
    }

    @Test
    @DisplayName("含 8D：8D 已走完 D8 且 CAPA 相位达措施审批 → 可闭环")
    void with8d_8dClosed_canClose() {
        ExceptionOrder o = baseOrder(ExceptionConstants.PROCESS_BOTH);
        o.setCapaPhase(ExceptionConstants.CAPA_PHASE_MEASURES_APPROVED); // BOTH 模式要求相位
        when(exceptionOrderMapper.selectById(100L)).thenReturn(o);
        when(rectificationPlanMapper.selectList(any())).thenReturn(List.of(donePlan()));
        when(improvementActionMapper.selectList(any())).thenReturn(List.of(doneAction()));
        when(verificationRecordMapper.selectList(any())).thenReturn(List.of(passedVerify()));
        when(exception8dMapper.selectByExceptionId(anyLong())).thenReturn(completed8d());

        CloseReadinessVO vo = exceptionService.closeReadiness(100L);

        assertThat(vo.isCanClose()).isTrue();
    }

    @Test
    @DisplayName("改善措施有未完成项 → 不可闭环")
    void capa_actionNotDone_cannotClose() {
        ExceptionOrder o = baseOrder(ExceptionConstants.PROCESS_CAPA);
        when(exceptionOrderMapper.selectById(100L)).thenReturn(o);
        when(rectificationPlanMapper.selectList(any())).thenReturn(List.of(donePlan()));
        ImprovementAction a = new ImprovementAction();
        a.setId(1L);
        a.setStatus(ExceptionConstants.ACTION_STATUS_PENDING); // 未完成
        when(improvementActionMapper.selectList(any())).thenReturn(List.of(a));
        when(verificationRecordMapper.selectList(any())).thenReturn(List.of(passedVerify()));
        when(exception8dMapper.selectByExceptionId(anyLong())).thenReturn(null);

        CloseReadinessVO vo = exceptionService.closeReadiness(100L);

        assertThat(vo.isCanClose()).isFalse();
        assertThat(vo.getChecks()).anyMatch(c -> "改善措施".equals(c.getItem()) && "FAIL".equals(c.getStatus()));
    }

    @Test
    @DisplayName("验证记录最新一条未通过 → 不可闭环")
    void verification_notPassed_cannotClose() {
        ExceptionOrder o = baseOrder(ExceptionConstants.PROCESS_CAPA);
        when(exceptionOrderMapper.selectById(100L)).thenReturn(o);
        when(rectificationPlanMapper.selectList(any())).thenReturn(List.of(donePlan()));
        when(improvementActionMapper.selectList(any())).thenReturn(List.of(doneAction()));
        VerificationRecord v = new VerificationRecord();
        v.setId(1L);
        v.setResult("不通过"); // 未通过
        v.setVerifierName("张三");
        v.setVerifyDate(LocalDate.now());
        when(verificationRecordMapper.selectList(any())).thenReturn(List.of(v));
        when(exception8dMapper.selectByExceptionId(anyLong())).thenReturn(null);

        CloseReadinessVO vo = exceptionService.closeReadiness(100L);

        assertThat(vo.isCanClose()).isFalse();
        assertThat(vo.getChecks()).anyMatch(c -> "验证记录".equals(c.getItem()) && "FAIL".equals(c.getStatus()));
    }

    @Test
    @DisplayName("纯 8D 流程：8D 已终审通过 + 改善措施完成 + 验证通过 → 可闭环")
    void pure8d_allDone_canClose() {
        ExceptionOrder o = baseOrder(ExceptionConstants.PROCESS_EIGHT_D);
        when(exceptionOrderMapper.selectById(100L)).thenReturn(o);
        when(rectificationPlanMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(improvementActionMapper.selectList(any())).thenReturn(List.of(doneAction()));
        when(verificationRecordMapper.selectList(any())).thenReturn(List.of(passedVerify()));
        when(exception8dMapper.selectByExceptionId(anyLong())).thenReturn(completed8d());

        CloseReadinessVO vo = exceptionService.closeReadiness(100L);

        assertThat(vo.isCanClose()).isTrue();
    }

    @Test
    @DisplayName("异常单不存在 → 抛 BusinessException(NOT_FOUND)")
    void notFound_throws() {
        when(exceptionOrderMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> exceptionService.closeReadiness(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode());
    }
}
