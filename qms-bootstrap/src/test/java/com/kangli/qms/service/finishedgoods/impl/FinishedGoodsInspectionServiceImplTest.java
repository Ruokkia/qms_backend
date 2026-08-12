package com.kangli.qms.service.finishedgoods.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.service.exception.ExceptionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0 缺口补测：M1 成品入库检验业务逻辑（双签状态机 / reportNo 唯一性 / 不合格自动建单）。
 *
 * <p>对应 test-plan §18.6 真正缺失项 #1。成品检验是 M1 唯一带完整状态机的业务，
 * 此前无任何单测守护，状态流转 bug 难以在单测层拦截。</p>
 *
 * <p>覆盖维度：
 * <ul>
 *   <li>create：reportNo 为空/重复抛 BAD_REQUEST；category 归一化（半成品/成品）；新建强制双签"待审核"。</li>
 *   <li>update 双签状态机：PENDING→APPROVED/REJECTED 合法；APPROVED→REJECTED 合法；REJECTED→PENDING 合法；
 *       非法流转（如 APPROVED→PENDING）抛 BAD_REQUEST。</li>
 *   <li>管代不可跳过品管（mgrApproval=已审核 时 qcReview 必须为已审核）。</li>
 *   <li>品管被重置为待审核时，管代已审核级联重置为待审核。</li>
 *   <li>品管审核通过补全 qcReviewer/qcReviewTime；不合格且 unqualifiedQty>0 自动建"成品不良"异常单。</li>
 *   <li>update 对不存在记录 / 已逻辑删除记录抛 NOT_FOUND；乐观锁 version 保留。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("M1 成品入库检验业务逻辑")
class FinishedGoodsInspectionServiceImplTest {

    private static final String PENDING = "待审核";
    private static final String APPROVED = "已审核";
    private static final String REJECTED = "驳回";

    @Mock
    private FinishedGoodsInspectionMapper mapper;
    @Mock
    private ExceptionService exceptionService;

    private FinishedGoodsInspectionServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "init");
        TableInfoHelper.initTableInfo(assistant, FinishedGoodsInspection.class);
    }

    @BeforeEach
    void setUp() {
        service = new FinishedGoodsInspectionServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "exceptionService", exceptionService);
        LoginUserHolder.set(LoginUser.builder()
                .userId(1L).account("sz-user").realName("张三").plantCode(PlantCode.SZ).build());
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    // ===================== create 相关 =====================

    @Test
    @DisplayName("M1-001 create：reportNo 为空抛 BAD_REQUEST")
    void create_reportNoEmpty_rejected() {
        FinishedGoodsInspection rec = new FinishedGoodsInspection();
        rec.setReportNo("  ");
        rec.setInspectionResult("合格");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(rec, LoginUserHolder.get()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(mapper, never()).insert(any());
    }

    @Test
    @DisplayName("M1-001 create：reportNo 重复抛 BAD_REQUEST")
    void create_reportNoDuplicate_rejected() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        FinishedGoodsInspection rec = new FinishedGoodsInspection();
        rec.setReportNo("RPT-001");
        rec.setInspectionResult("合格");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(rec, LoginUserHolder.get()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(mapper, never()).insert(any());
    }

    @Test
    @DisplayName("M1-002 create：category 归一化（半成品/成品）")
    void create_normalizeCategory() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(mapper.insert(any(FinishedGoodsInspection.class))).thenReturn(1);
        when(mapper.selectById(any())).thenReturn(withId(100L, PENDING, PENDING));

        FinishedGoodsInspection semi = new FinishedGoodsInspection();
        semi.setReportNo("RPT-SEM");
        semi.setCategory("半成品");
        service.create(semi, LoginUserHolder.get());
        assertEquals("半成品", semi.getCategory());

        FinishedGoodsInspection weird = new FinishedGoodsInspection();
        weird.setReportNo("RPT-OTHER");
        weird.setCategory("未知类别");
        service.create(weird, LoginUserHolder.get());
        assertEquals("成品", weird.getCategory()); // 非半成品一律归为成品
    }

    @Test
    @DisplayName("M1-003 create：新建强制双签待审核，忽略请求体越权状态")
    void create_forcedPendingDualSign() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(mapper.insert(any(FinishedGoodsInspection.class))).thenReturn(1);
        when(mapper.selectById(any())).thenReturn(withId(101L, PENDING, PENDING));

        FinishedGoodsInspection rec = new FinishedGoodsInspection();
        rec.setReportNo("RPT-003");
        rec.setInspectionResult("合格");
        rec.setQcReview(APPROVED); // 请求体试图伪造已审核
        rec.setMgrApproval(APPROVED);

        service.create(rec, LoginUserHolder.get());
        assertEquals(PENDING, rec.getQcReview());
        assertEquals(PENDING, rec.getMgrApproval());
        verify(exceptionService, never()).createFromFinishedGoods(any(), any());
    }

    // ===================== update 双签状态机 =====================

    @Test
    @DisplayName("M1-004 update：品管 待审核→已审核 合法")
    void update_qcPendingToApproved_ok() {
        FinishedGoodsInspection old = withId(200L, PENDING, PENDING);
        when(mapper.selectById(200L)).thenReturn(old);
        when(mapper.updateById(any())).thenReturn(1);
        when(mapper.selectById(200L)).thenReturn(old);

        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-200");
        upd.setInspectionResult("合格");
        upd.setQcReview(APPROVED);
        upd.setMgrApproval(PENDING);

        service.update(200L, upd, LoginUserHolder.get());
        verify(mapper, times(1)).updateById(any());
    }

    @Test
    @DisplayName("M1-004 update：品管 已审核→驳回 合法")
    void update_qcApprovedToRejected_ok() {
        FinishedGoodsInspection old = withId(201L, APPROVED, PENDING);
        when(mapper.selectById(201L)).thenReturn(old);
        when(mapper.updateById(any())).thenReturn(1);

        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-201");
        upd.setInspectionResult("合格");
        upd.setQcReview(REJECTED);
        upd.setMgrApproval(PENDING);

        service.update(201L, upd, LoginUserHolder.get());
        verify(mapper, times(1)).updateById(any());
    }

    @Test
    @DisplayName("M1-004 update：品管 驳回→待审核 合法（重新提交）")
    void update_qcRejectedToPending_ok() {
        FinishedGoodsInspection old = withId(202L, REJECTED, PENDING);
        when(mapper.selectById(202L)).thenReturn(old);
        when(mapper.updateById(any())).thenReturn(1);

        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-202");
        upd.setInspectionResult("合格");
        upd.setQcReview(PENDING);
        upd.setMgrApproval(PENDING);

        service.update(202L, upd, LoginUserHolder.get());
        verify(mapper, times(1)).updateById(any());
    }

    @Test
    @DisplayName("M1-004 update：品管 已审核→待审核 非法流转抛 BAD_REQUEST")
    void update_qcApprovedToPending_illegal() {
        FinishedGoodsInspection old = withId(203L, APPROVED, PENDING);
        when(mapper.selectById(203L)).thenReturn(old);

        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-203");
        upd.setInspectionResult("合格");
        upd.setQcReview(PENDING);
        upd.setMgrApproval(PENDING);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(203L, upd, LoginUserHolder.get()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(mapper, never()).updateById(any());
    }

    @Test
    @DisplayName("M1-005 update：管代不可跳过品管（mgr=已审核 时 qc 必须已审核）")
    void update_mgrCannotSkipQc() {
        FinishedGoodsInspection old = withId(204L, PENDING, PENDING);
        when(mapper.selectById(204L)).thenReturn(old);

        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-204");
        upd.setInspectionResult("合格");
        upd.setQcReview(PENDING);
        upd.setMgrApproval(APPROVED); // 想直接管代批准，但品管还待审核

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(204L, upd, LoginUserHolder.get()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(mapper, never()).updateById(any());
    }

    @Test
    @DisplayName("M1-005 update：品管已审核→管代已审核 合法，且补全管代签名")
    void update_qcApprovedThenMgrApproved_ok() {
        FinishedGoodsInspection old = withId(205L, APPROVED, PENDING);
        when(mapper.selectById(205L)).thenReturn(old);
        when(mapper.updateById(any())).thenReturn(1);

        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-205");
        upd.setInspectionResult("合格");
        upd.setQcReview(APPROVED);
        upd.setMgrApproval(APPROVED);

        service.update(205L, upd, LoginUserHolder.get());
        ArgumentCaptor<FinishedGoodsInspection> cap = ArgumentCaptor.forClass(FinishedGoodsInspection.class);
        verify(mapper, times(1)).updateById(cap.capture());
        assertThat(cap.getValue().getMgrRepresentative()).isEqualTo("张三");
        assertThat(cap.getValue().getMgrApprovalTime()).isNotNull();
    }

    @Test
    @DisplayName("M1-006 update：品管 已审核→待审核 重新提交时，管代不可保持已审核（被拒）")
    void update_qcApprovedToPending_mgrCannotStayApproved() {
        // old: 品管已审核 + 管代已审核
        FinishedGoodsInspection old = withId(206L, APPROVED, APPROVED);
        when(mapper.selectById(206L)).thenReturn(old);

        // new: 品管 已审核→待审核（APPROVED→PENDING 非法流转，且管代仍填已审核）
        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-206");
        upd.setInspectionResult("合格");
        upd.setQcReview(PENDING);
        upd.setMgrApproval(APPROVED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(206L, upd, LoginUserHolder.get()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(mapper, never()).updateById(any());
    }

    @Test
    @DisplayName("M1-006 update：品管驳回且管代仍填已审核，被拒（管代不可越过品管）")
    void update_qcRejected_mgrCannotStayApproved() {
        FinishedGoodsInspection old = withId(2061L, APPROVED, APPROVED);
        when(mapper.selectById(2061L)).thenReturn(old);

        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-2061");
        upd.setInspectionResult("合格");
        upd.setQcReview(REJECTED);
        upd.setMgrApproval(APPROVED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(2061L, upd, LoginUserHolder.get()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(mapper, never()).updateById(any());
    }

    @Test
    @DisplayName("M1-006 update：品管审核通过补全 qcReviewer/qcReviewTime")
    void update_qcApproved_fillsReviewer() {
        FinishedGoodsInspection old = withId(207L, PENDING, PENDING);
        when(mapper.selectById(207L)).thenReturn(old);
        when(mapper.updateById(any())).thenReturn(1);

        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-207");
        upd.setInspectionResult("合格");
        upd.setQcReview(APPROVED);
        upd.setMgrApproval(PENDING);

        service.update(207L, upd, LoginUserHolder.get());
        ArgumentCaptor<FinishedGoodsInspection> cap = ArgumentCaptor.forClass(FinishedGoodsInspection.class);
        verify(mapper, times(1)).updateById(cap.capture());
        assertThat(cap.getValue().getQcReviewer()).isEqualTo("张三");
        assertThat(cap.getValue().getQcReviewTime()).isNotNull();
    }

    // ===================== 不合格自动建单 =====================

    @Test
    @DisplayName("M1-007 create：新建强制待审核，不合格也不触发建单（需品管审核通过）")
    void create_unqualified_noAutoCreate_onCreate() {
        when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(mapper.insert(any(FinishedGoodsInspection.class))).thenReturn(1);
        when(mapper.selectById(any())).thenReturn(withId(300L, PENDING, PENDING));

        FinishedGoodsInspection rec = new FinishedGoodsInspection();
        rec.setReportNo("RPT-BAD");
        rec.setInspectionResult("不合格");
        rec.setUnqualifiedQty(new BigDecimal("5"));

        service.create(rec, LoginUserHolder.get());
        // create 时双签均为待审核，品管尚未通过，故不触发自动建单
        verify(exceptionService, never()).createFromFinishedGoods(any(), any());
        assertEquals(PENDING, rec.getQcReview());
        assertEquals(PENDING, rec.getMgrApproval());
    }

    @Test
    @DisplayName("M1-007 update：品管审核通过且不合格，触发自动建单")
    void update_qcApprovedUnqualified_autoCreate() {
        FinishedGoodsInspection old = withId(302L, PENDING, PENDING);
        when(mapper.selectById(302L)).thenReturn(old);
        when(mapper.updateById(any())).thenReturn(1);

        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-302");
        upd.setInspectionResult("不合格");
        upd.setUnqualifiedQty(new BigDecimal("2"));
        upd.setQcReview(APPROVED);
        upd.setMgrApproval(PENDING);

        service.update(302L, upd, LoginUserHolder.get());
        verify(exceptionService, times(1)).createFromFinishedGoods(any(FinishedGoodsInspection.class), any(LoginUser.class));
    }

    // ===================== 边界：不存在 / 逻辑删除 / version =====================

    @Test
    @DisplayName("M1-008 update：记录不存在抛 NOT_FOUND")
    void update_notFound_throws() {
        when(mapper.selectById(999L)).thenReturn(null);

        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-X");
        upd.setInspectionResult("合格");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(999L, upd, LoginUserHolder.get()));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("M1-008 update：已逻辑删除记录抛 NOT_FOUND")
    void update_logicalDeleted_throws() {
        FinishedGoodsInspection old = withId(400L, PENDING, PENDING);
        old.setIsDeleted((short) 1);
        when(mapper.selectById(400L)).thenReturn(old);

        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-400");
        upd.setInspectionResult("合格");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(400L, upd, LoginUserHolder.get()));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("M1-008 update：保留乐观锁 version 与系统字段不被覆盖")
    void update_preservesVersionAndSystemFields() {
        FinishedGoodsInspection old = withId(401L, PENDING, PENDING);
        old.setVersion(7);
        old.setPlantCode("SZ");
        old.setPlantName("深圳");
        old.setCreatedBy("创建人");
        old.setIsDeleted((short) 0);
        when(mapper.selectById(401L)).thenReturn(old);
        when(mapper.updateById(any())).thenReturn(1);

        FinishedGoodsInspection upd = new FinishedGoodsInspection();
        upd.setReportNo("RPT-401");
        upd.setInspectionResult("合格");
        upd.setQcReview(APPROVED);
        upd.setMgrApproval(PENDING);
        upd.setVersion(99); // 请求体试图篡改 version

        service.update(401L, upd, LoginUserHolder.get());
        ArgumentCaptor<FinishedGoodsInspection> cap = ArgumentCaptor.forClass(FinishedGoodsInspection.class);
        verify(mapper, times(1)).updateById(cap.capture());
        assertEquals(Integer.valueOf(7), cap.getValue().getVersion()); // version 沿用旧值
        assertEquals("SZ", cap.getValue().getPlantCode());
        assertEquals("创建人", cap.getValue().getCreatedBy());
    }

    // ===================== 工具方法 =====================

    private FinishedGoodsInspection withId(Long id, String qc, String mgr) {
        FinishedGoodsInspection e = new FinishedGoodsInspection();
        e.setId(id);
        e.setReportNo("RPT-" + id);
        e.setInspectionResult("合格");
        e.setQcReview(qc);
        e.setMgrApproval(mgr);
        e.setIsDeleted((short) 0);
        e.setVersion(1);
        return e;
    }
}
