package com.kangli.qms.service.exception.impl;

import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.vo.QualityExceptionDecisionVO;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.service.exception.impl.QualityExceptionRuleEvaluator;
import com.kangli.qms.service.notification.NotificationConfigService;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.admin.AdminService;
import com.kangli.qms.service.admin.AuditLogService;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0 缺口补测：M1 来料不合格自动建异常单。
 *
 * <p>覆盖维度（对应 test-plan §10.2 / M1-010）：
 * <ul>
 *   <li>不合格来料经 createFromMaterialInspection 自动创建异常单（insert 被调用）。</li>
 *   <li>异常单 plantCode 以来料记录为准，不随当前操作用户厂错位（M1-010 修复断言）。</li>
 *   <li>已存在关联异常单时不重复创建（幂等）。</li>
 * </ul>
 *
 * <p>原 test-plan 将 M1-010 标注为锁现状（跨厂错位），源码已以来料 plantCode 为准，
 * 因此本测试写「正确行为」断言作为回归守护。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("M1 来料自动建异常单")
class CreateFromMaterialInspectionTest {

    @Mock
    private ExceptionOrderMapper exceptionOrderMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private MaterialInspectionMapper materialInspectionMapper;
    @Mock
    private QualityExceptionRuleEvaluator qualityRuleEvaluator;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationConfigService notificationConfigService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AdminService adminService;

    private ExceptionServiceImpl exceptionService;

    @BeforeEach
    void setUp() {
        LoginUserHolder.set(LoginUser.builder()
                .userId(1L).account("sz-user").plantCode(PlantCode.SZ).build()); // 当前操作者是 SZ，但来料是 MZ

        exceptionService = new ExceptionServiceImpl(
                exceptionOrderMapper, null, null, supplierMapper, null, null, notificationService,
                materialInspectionMapper, null, null, null, null, auditLogService, null,
                qualityRuleEvaluator, adminService, notificationConfigService);
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private MaterialInspection mzInspection() {
        MaterialInspection m = new MaterialInspection();
        m.setId(777L);
        m.setPlantCode("MZ");
        m.setSupplierCode("SUP-1");
        m.setMaterialCode("MAT-1");
        m.setMaterialBatchNo("B-1");
        m.setInspectionDate(LocalDate.now());
        m.setInspectionResult("不合格");
        m.setUnqualifiedQty(new BigDecimal("3"));
        return m;
    }

    @Test
    @DisplayName("M1-010 不合格来料自动建单，且异常单 plantCode 以来料(MZ)为准")
    void autoCreate_usesInspectionPlantCode() {
        when(qualityRuleEvaluator.evaluate(any(MaterialInspection.class), anyInt()))
                .thenReturn(decision());
        when(supplierMapper.selectOne(any())).thenReturn(null);
        when(materialInspectionMapper.countUnqualifiedBatches(any(), any(), any(), any(), any())).thenReturn(0);
        when(notificationConfigService.getReceivingRoleCodes(any())).thenReturn(java.util.Collections.emptyList());
        when(exceptionOrderMapper.selectOne(any())).thenReturn(null); // 无已关联单
        when(exceptionOrderMapper.selectCount(any())).thenReturn(0L); // 近90天0批

        ExceptionOrder order = exceptionService.createFromMaterialInspection(mzInspection(), LoginUserHolder.get());

        assertThat(order).isNotNull();
        assertThat(order.getPlantCode()).isEqualTo("MZ");
        verify(exceptionOrderMapper, times(1)).insert(any(ExceptionOrder.class));
    }

    @Test
    @DisplayName("M1-010 重复调用已关联异常单时不再建新单（幂等）")
    void autoCreate_idempotent_whenAlreadyLinked() {
        when(qualityRuleEvaluator.evaluate(any(MaterialInspection.class), anyInt()))
                .thenReturn(decision());
        when(supplierMapper.selectOne(any())).thenReturn(null);
        when(materialInspectionMapper.countUnqualifiedBatches(any(), any(), any(), any(), any())).thenReturn(0);
        when(notificationConfigService.getReceivingRoleCodes(any())).thenReturn(java.util.Collections.emptyList());
        ExceptionOrder existing = new ExceptionOrder();
        existing.setId(555L);
        existing.setExceptionNo("EX-EXIST");
        existing.setPlantCode("MZ");
        when(exceptionOrderMapper.selectOne(any())).thenReturn(existing); // 已关联
        when(exceptionOrderMapper.selectById(555L)).thenReturn(existing);

        ExceptionOrder order = exceptionService.createFromMaterialInspection(mzInspection(), LoginUserHolder.get());

        assertThat(order.getId()).isEqualTo(555L);
        verify(exceptionOrderMapper, times(0)).insert(any(ExceptionOrder.class));
    }

    private QualityExceptionDecisionVO decision() {
        QualityExceptionDecisionVO d = new QualityExceptionDecisionVO();
        d.setSeverity("MAJOR");
        d.setProcessType("CAPA");
        d.setNotificationLevel("PLANT");
        d.setResponseHours(24);
        d.setDeadlineDays(7);
        d.setRuleReason("来料不合格自动判定");
        return d;
    }
}
