package com.kangli.qms.service.exception.impl;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.domain.exception.mapper.EscalationMapper;
import com.kangli.qms.domain.exception.mapper.Exception8dMapper;
import com.kangli.qms.domain.exception.mapper.ExceptionOrderMapper;
import com.kangli.qms.domain.exception.mapper.ImprovementActionMapper;
import com.kangli.qms.domain.exception.mapper.RectificationPlanMapper;
import com.kangli.qms.domain.exception.mapper.VerificationRecordMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionRecordMapper;
import com.kangli.qms.domain.finishedgoods.mapper.FinishedGoodsInspectionMapper;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.service.admin.AdminService;
import com.kangli.qms.service.admin.AuditLogService;
import com.kangli.qms.service.exception.ExceptionApprovalConfigService;
import com.kangli.qms.service.exception.dto.ExceptionCloseDTO;
import com.kangli.qms.service.exception.dto.ExceptionInitiateDTO;
import com.kangli.qms.service.exception.dto.ExceptionUpdateDTO;
import com.kangli.qms.service.exception.impl.QualityExceptionRuleEvaluator;
import com.kangli.qms.service.notification.NotificationConfigService;
import com.kangli.qms.service.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M2 异常整改单核心状态机单测（纯 mock）。
 * 覆盖 M2-001（创建编号+初始态）、M2-002（initiate 三分支）、
 * M2-005（已闭环再 close 拒 400）、M2-008（reset 守卫）、M2-009（update 守卫）。
 *
 * 说明：M2-008「已闭环清空并逻辑删除关联记录」分支内部使用 MyBatis-Plus
 * LambdaUpdateWrapper，构建时需解析实体 lambda 缓存（依赖 Spring/MP 运行上下文），
 * 纯 mock 单测无法注册该缓存，因此本测试仅覆盖其守卫分支（仅「已闭环」可重置）；
 * 清空与级联逻辑删除由集成测试负责验证。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExceptionServiceImplTest {

    private static final long EXCEPTION_ID = 1L;

    @Mock
    private ExceptionOrderMapper exceptionOrderMapper;
    @Mock
    private ImprovementActionMapper improvementActionMapper;
    @Mock
    private VerificationRecordMapper verificationRecordMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private EscalationMapper escalationMapper;
    @Mock
    private Exception8dMapper exception8dMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MaterialInspectionMapper materialInspectionMapper;
    @Mock
    private FaiInspectionRecordMapper faiInspectionRecordMapper;
    @Mock
    private FinishedGoodsInspectionMapper finishedGoodsInspectionMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private AuditLogMapper auditLogMapper;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private RectificationPlanMapper rectificationPlanMapper;
    @Mock
    private QualityExceptionRuleEvaluator qualityRuleEvaluator;
    @Mock
    private AdminService adminService;
    @Mock
    private NotificationConfigService notificationConfigService;

    private ExceptionServiceImpl exceptionService;

    @BeforeEach
    void setUp() throws Exception {
        exceptionService = new ExceptionServiceImpl(
                exceptionOrderMapper, improvementActionMapper, verificationRecordMapper,
                supplierMapper, escalationMapper, exception8dMapper, notificationService,
                materialInspectionMapper, faiInspectionRecordMapper, finishedGoodsInspectionMapper,
                sysUserMapper, auditLogMapper, auditLogService, rectificationPlanMapper,
                qualityRuleEvaluator, adminService, notificationConfigService);

        ExceptionApprovalConfigService approvalConfigService = mock(ExceptionApprovalConfigService.class);
        Field field = ExceptionServiceImpl.class.getDeclaredField("approvalConfigService");
        field.setAccessible(true);
        field.set(exceptionService, approvalConfigService);

        // R06 质量经理：具备 create/initiate/close/reset/update 的全部角色权限，且 plantCode 与测试订单一致
        LoginUserHolder.set(LoginUser.builder()
                .userId(1001L)
                .account("qmgr01")
                .realName("质量经理")
                .roleCode("R06")
                .plantCode(PlantCode.SZ)
                .build());

        // 通知配置桩：返回空角色列表，避免真实发送通知链路 NPE
        when(notificationConfigService.getReceivingRoleCodes(any())).thenReturn(Collections.emptyList());
        when(notificationConfigService.listUserIdsByRoleCodes(any(), any())).thenReturn(Collections.emptyList());
        when(notificationConfigService.getSeverityExtraRoleCodes(any())).thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    // ==================== M2-001：创建异常单 ====================

    @Test
    @DisplayName("M2-001 创建异常单：编号格式 EX-YYYYMMDD-NNN，初始 status=待整改、capaStatus=待发起")
    void testCreate_generatesValidNoAndInitialStatus() {
        when(exceptionOrderMapper.selectOne(any())).thenReturn(null);
        when(exceptionOrderMapper.insert(any(ExceptionOrder.class))).thenReturn(1);

        ExceptionOrder req = new ExceptionOrder();
        req.setSourceType("来料不良");
        req.setDefectDesc("外壳划伤");

        ExceptionOrder result = exceptionService.create(req);

        assertNotNull(result);
        assertTrue(result.getExceptionNo().matches("EX-\\d{8}-\\d{3}"),
                "异常单编号应符合 EX-YYYYMMDD-NNN 格式，实际：" + result.getExceptionNo());
        assertEquals("待整改", result.getStatus(), "初始 status 应为 待整改");
        assertEquals("待发起", result.getCapaStatus(), "初始 capaStatus 应为 待发起");
        assertNull(result.getProcessType(), "初始 processType 应为 null（未选流程）");
        verify(exceptionOrderMapper).insert(any(ExceptionOrder.class));
    }

    // ==================== M2-002：initiate 三分支 ====================

    @Test
    @DisplayName("M2-002 合法 CAPA 流程发起：status=整改中、capaStatus=进行中")
    void testInitiate_capa_advancesToInProgress() {
        ExceptionOrder existing = buildOrder("待整改", "待发起", null);
        when(exceptionOrderMapper.selectById(EXCEPTION_ID)).thenReturn(existing);

        ExceptionInitiateDTO dto = new ExceptionInitiateDTO();
        dto.setProcessType("CAPA");
        dto.setD0Symptom("来料外壳划伤");

        exceptionService.initiate(EXCEPTION_ID, dto);

        ArgumentCaptor<ExceptionOrder> cap = ArgumentCaptor.forClass(ExceptionOrder.class);
        verify(exceptionOrderMapper).updateById(cap.capture());
        ExceptionOrder saved = cap.getValue();
        assertEquals("CAPA", saved.getProcessType());
        assertEquals("整改中", saved.getStatus());
        assertEquals("进行中", saved.getCapaStatus());
    }

    @Test
    @DisplayName("M2-002 非法 processType：抛出 400 不支持的整改流程类型")
    void testInitiate_invalidProcessType_throws400() {
        ExceptionOrder existing = buildOrder("待整改", "待发起", null);
        when(exceptionOrderMapper.selectById(EXCEPTION_ID)).thenReturn(existing);

        ExceptionInitiateDTO dto = new ExceptionInitiateDTO();
        dto.setProcessType("XYZ");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exceptionService.initiate(EXCEPTION_ID, dto));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("无效的整改流程类型"));
    }

    @Test
    @DisplayName("M2-002 已发起再发起：抛出 400 不可重复发起")
    void testInitiate_alreadyInitiated_throws400() {
        ExceptionOrder existing = buildOrder("整改中", "进行中", "CAPA");
        when(exceptionOrderMapper.selectById(EXCEPTION_ID)).thenReturn(existing);

        ExceptionInitiateDTO dto = new ExceptionInitiateDTO();
        dto.setProcessType("CAPA");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exceptionService.initiate(EXCEPTION_ID, dto));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("可发起流程"));
    }

    // ==================== M2-005：已闭环再 close ====================

    @Test
    @DisplayName("M2-005 已闭环异常单再 close：抛出 400 异常单已闭环")
    void testClose_alreadyClosed_throws400() {
        ExceptionOrder existing = buildOrder("已闭环", "已完成", "CAPA");
        when(exceptionOrderMapper.selectById(EXCEPTION_ID)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exceptionService.close(EXCEPTION_ID, new ExceptionCloseDTO()));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("异常单已闭环"));
    }

    // ==================== M2-008：reset 守卫 ====================

    @Test
    @DisplayName("M2-008 非闭环异常单 reset：抛出 400 仅已闭环可重置")
    void testReset_notClosed_throws400() {
        ExceptionOrder existing = buildOrder("整改中", "进行中", "CAPA");
        when(exceptionOrderMapper.selectById(EXCEPTION_ID)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exceptionService.reset(EXCEPTION_ID));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("已闭环"));
    }

    // ==================== M2-009：update 守卫 ====================

    @Test
    @DisplayName("M2-009 update 守卫：status/closedAt 强制忽略为 null，不可改闭环态")
    void testUpdate_ignoresStatusAndClosedAt() {
        ExceptionOrder existing = buildOrder("整改中", "进行中", "CAPA");
        when(exceptionOrderMapper.selectById(EXCEPTION_ID)).thenReturn(existing);
        when(exceptionOrderMapper.updateById(any(ExceptionOrder.class))).thenReturn(1);

        ExceptionUpdateDTO dto = new ExceptionUpdateDTO();
        dto.setDefectDesc("更新后的不良描述");

        exceptionService.update(EXCEPTION_ID, dto);

        ArgumentCaptor<ExceptionOrder> cap = ArgumentCaptor.forClass(ExceptionOrder.class);
        verify(exceptionOrderMapper).updateById(cap.capture());
        ExceptionOrder saved = cap.getValue();
        assertNull(saved.getStatus(), "update 后 status 必须为 null（禁止改闭环态）");
        assertNull(saved.getClosedAt(), "update 后 closedAt 必须为 null");
        assertEquals("更新后的不良描述", saved.getDefectDesc());
    }

    // ==================== 工具方法 ====================

    private ExceptionOrder buildOrder(String status, String capaStatus, String processType) {
        ExceptionOrder o = new ExceptionOrder();
        o.setId(EXCEPTION_ID);
        o.setExceptionNo("EX-20260810-001");
        o.setStatus(status);
        o.setCapaStatus(capaStatus);
        o.setProcessType(processType);
        o.setPlantCode("SZ");
        o.setPlantName("深圳");
        o.setCreatedBy("qmgr01");
        o.setSourceType("来料不良");
        return o;
    }
}
