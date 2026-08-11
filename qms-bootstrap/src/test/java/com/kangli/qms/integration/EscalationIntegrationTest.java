package com.kangli.qms.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.exception.entity.Escalation;
import com.kangli.qms.domain.exception.mapper.EscalationMapper;
import com.kangli.qms.domain.incoming.entity.MaterialInspection;
import com.kangli.qms.domain.incoming.mapper.MaterialInspectionMapper;
import com.kangli.qms.domain.supplier.entity.Supplier;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.service.exception.ExceptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 供应商升级集成测试（test-plan M2-043 建单/检测、M2-045 跨厂 404、M2-044 分级措施）。
 * 走真实拦截器链 + DB(qms)；@Transactional 回滚建单改动。
 */
class EscalationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SupplierMapper supplierMapper;
    @Autowired
    private EscalationMapper escalationMapper;
    @Autowired
    private MaterialInspectionMapper materialInspectionMapper;
    @Autowired
    private ExceptionService exceptionService;

    private static final String SZ_MATERIAL = "MC-001";

    /** 取一个真实存在的 SZ 启用供应商（有 supplierId 才能触发自动升级）。 */
    private Supplier szSupplier() {
        return supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getPlantCode, "SZ")
                .eq(Supplier::getStatus, "启用")
                .last("LIMIT 1"));
    }

    /** 构造 SZ 厂登录用户（触发自动升级所需最小字段）。 */
    private LoginUser szLoginUser(Long userId) {
        return LoginUser.builder()
                .userId(userId)
                .realName("集成测试")
                .account("sz_mgr01")
                .roleCode("R03")
                .plantCode(PlantCode.SZ)
                .canSwitchArea(false)
                .build();
    }

    /** 插入一批 90 天内不合格来料（同供应商同物料），返回主键。 */
    private long insertUnqualifiedBatch(String supplierCode, String materialCode, LocalDate inspectionDate) {
        MaterialInspection m = new MaterialInspection();
        m.setPlantCode("SZ");
        m.setRecordNo("IT-ESC-" + System.nanoTime() + "-" + inspectionDate);
        m.setSupplierCode(supplierCode);
        m.setSupplierName("集成测试供应商");
        m.setMaterialCode(materialCode);
        m.setInspectionResult("不合格");
        m.setInspectionDate(inspectionDate);
        m.setDefectDesc("集成测试不合格");
        m.setUnqualifiedQty(BigDecimal.valueOf(5));
        m.setSubmittedQty(BigDecimal.valueOf(100));
        m.setPlantName("深圳");
        m.setCreatedBy("集成测试");
        m.setUpdatedBy("集成测试");
        materialInspectionMapper.insert(m);
        return m.getId();
    }

    /** 预置 N 条 180 天内历史 Escalation（CLOSED，计入 previousCount 但不拦截触发）。 */
    private void seedPreviousEscalations(int n, Supplier supplier) {
        for (int i = 0; i < n; i++) {
            Escalation e = new Escalation();
            e.setSupplierId(String.valueOf(supplier.getId()));
            e.setSupplierCode(supplier.getSupplierCode());
            e.setSupplierName(supplier.getSupplierName());
            e.setMaterialCode(SZ_MATERIAL);
            e.setEscalationReason("历史升级" + i);
            e.setEscalationAction("历史措施");
            e.setStatus("CLOSED");
            e.setPlantCode("SZ");
            e.setPlantName("深圳");
            e.setCreatedBy("SYSTEM");
            e.setUpdatedBy("SYSTEM");
            e.setCreatedAt(LocalDateTime.now().minusDays(30));
            e.setVersion(0);
            escalationMapper.insert(e);
        }
    }

    /** 触发自动升级并返回本次新建升级单的 escalationAction（按 id 阈值精确定位）。 */
    private String triggerAutoEscalationAndGetAction(Long userId, Supplier supplier) {
        Long baseline = escalationMapper.selectObjs(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Escalation>().select("max(id)"))
                .stream().findFirst().map(o -> ((Number) o).longValue()).orElse(0L);
        LoginUser loginUser = szLoginUser(userId);
        LoginUserHolder.set(loginUser); // auditLogService 从 LoginUserHolder 取 plantCode
        try {
            long batchId = insertUnqualifiedBatch(supplier.getSupplierCode(), SZ_MATERIAL, LocalDate.now().minusDays(1));
            MaterialInspection inspection = materialInspectionMapper.selectById(batchId);
            exceptionService.createFromMaterialInspection(inspection, loginUser);
        } finally {
            LoginUserHolder.clear();
        }
        // 仅取本次自动建的单（id 大于基线、PENDING_REVIEW、同供应商同物料）
        Escalation latest = escalationMapper.selectOne(new LambdaQueryWrapper<Escalation>()
                .gt(Escalation::getId, baseline)
                .eq(Escalation::getPlantCode, "SZ")
                .eq(Escalation::getSupplierCode, supplier.getSupplierCode())
                .eq(Escalation::getMaterialCode, SZ_MATERIAL)
                .eq(Escalation::getStatus, "PENDING_REVIEW")
                .orderByDesc(Escalation::getId)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getEscalationAction();
    }

    /** M2-044 tier0：180 天内无历史升级 -> 加严检验、提高审核频次、专项8D。 */
    @Test
    void m2_044_tier0_noPreviousHistory_escalateStrictInspection() throws Exception {
        Long userId = loginUserId("sz_mgr01", "123456");
        Supplier supplier = szSupplier();
        // 确保 90 天内≥3 批不合格（含本批共插入 3 批）
        insertUnqualifiedBatch(supplier.getSupplierCode(), SZ_MATERIAL, LocalDate.now().minusDays(2));
        insertUnqualifiedBatch(supplier.getSupplierCode(), SZ_MATERIAL, LocalDate.now().minusDays(3));
        String action = triggerAutoEscalationAndGetAction(userId, supplier);
        assertEquals("加严检验、提高审核频次、专项8D", action);
    }

    /** M2-044 tier1：180 天内 1 次历史升级 -> 建议降低采购份额20%。 */
    @Test
    void m2_044_tier1_onePreviousHistory_reduceShare() throws Exception {
        Long userId = loginUserId("sz_mgr01", "123456");
        Supplier supplier = szSupplier();
        seedPreviousEscalations(1, supplier);
        insertUnqualifiedBatch(supplier.getSupplierCode(), SZ_MATERIAL, LocalDate.now().minusDays(2));
        insertUnqualifiedBatch(supplier.getSupplierCode(), SZ_MATERIAL, LocalDate.now().minusDays(3));
        String action = triggerAutoEscalationAndGetAction(userId, supplier);
        assertEquals("建议降低采购份额20%", action);
    }

    /** M2-044 tier2：180 天内 ≥2 次历史升级 -> 建议暂停新增采购或暂停供货。 */
    @Test
    void m2_044_tier2_twoOrMorePreviousHistory_suspendSupply() throws Exception {
        Long userId = loginUserId("sz_mgr01", "123456");
        Supplier supplier = szSupplier();
        seedPreviousEscalations(2, supplier);
        insertUnqualifiedBatch(supplier.getSupplierCode(), SZ_MATERIAL, LocalDate.now().minusDays(2));
        insertUnqualifiedBatch(supplier.getSupplierCode(), SZ_MATERIAL, LocalDate.now().minusDays(3));
        String action = triggerAutoEscalationAndGetAction(userId, supplier);
        assertEquals("建议暂停新增采购或暂停供货", action);
    }

    /** M2-043：90 天内同供应商同物料≥3 次不良 -> 检测返回应升级候选（demo 已插入 3 批 SUP-SZ-01）。 */
    @Test
    void check_returnsTriggeredSupplier_forRepeatDefects() throws Exception {
        String token = login("sz_mgr01", "123456");
        mvc.perform(post("/api/v1/escalations/check")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalChecked").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.triggeredSuppliers[0].shouldEscalate").value(true));
    }

    /** M2-043：对检测到的候选发起升级 -> 建 PENDING_REVIEW 单。 */
    @Test
    void create_escalationFromTriggeredSupplier_pendingReview() throws Exception {
        String token = login("sz_mgr01", "123456");
        long supId = szSupplier().getId();
        mvc.perform(post("/api/v1/escalations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supId
                                + ",\"materialCode\":\"MC-001\""
                                + ",\"escalationReason\":\"集成测试升级\""
                                + ",\"escalationAction\":\"加密审核并暂停供货\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.processStage").value("PENDING_REVIEW"));
    }

    /** M2-043：供应商不存在 -> 404 NOT_FOUND。 */
    @Test
    void create_withUnknownSupplier_returns404() throws Exception {
        String token = login("sz_mgr01", "123456");
        mvc.perform(post("/api/v1/escalations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":99999999"
                                + ",\"escalationReason\":\"x\""
                                + ",\"escalationAction\":\"y\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** M2-045：R06 切到 MZ 上下文访问 SZ 升级记录 -> 跨厂 404（requireEscalation 校验 plantCode）。 */
    @Test
    void r06_switchToMZ_accessSzEscalation_returns404() throws Exception {
        String token = login("sz_mgr01", "123456");
        long supId = szSupplier().getId();
        String body = mvc.perform(post("/api/v1/escalations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supId
                                + ",\"materialCode\":\"MC-001\""
                                + ",\"escalationReason\":\"跨厂隔离测试\""
                                + ",\"escalationAction\":\"加密审核\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();

        mvc.perform(post("/api/v1/escalations/" + id + "/review")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Plant-Code", "MZ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"opinion\":\"跨厂\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}
