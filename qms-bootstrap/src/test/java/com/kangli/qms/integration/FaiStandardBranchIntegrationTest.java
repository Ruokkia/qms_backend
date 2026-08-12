package com.kangli.qms.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3 首件检验 —— 标准/建单集成分支回归（计划 §18.6「仍缺失」清单第4条）。
 *
 * 覆盖：
 * - M3-005 (P0)：无激活标准时建单成功且参数项为空（不阻塞首件发起）
 * - M3-006 (P0)：建单编号生成规则 faiNo = FAI-{plant}-{yyyyMMdd}-NNN
 * - M3-012 (P1)：建单时 inspection item 的 USL/LSL/标准值从激活标准复制（数值覆盖）
 * - M3-013 (P1)：同一 物料代码+工序 第二次建标准，版本号 = 第一次 + 1（标准版本递增）
 * - M3-015 (P2)：refreshStandard 刷新后置——改标准后刷新，已存在项同步最新标准值、新增参数追加为检验项
 *
 * 账号：sz_mgr01(R06 全权限)，单厂 SZ 操作，避免跨厂租户过滤干扰分支断言。
 */
class FaiStandardBranchIntegrationTest extends BaseIntegrationTest {

    private static final String SZ_MGR = "sz_mgr01";
    private static final String PWD = "123456";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 测试库为共享开发库，FAI 各表自增 id 的 identity 序列可能落后于既有历史数据
     * （insert 不指定 id 时拿到与历史数据冲突的 id，导致唯一约束冲突）。
     * 每个用例前把相关表的 identity 序列推到「当前最大值 + 1000」，规避主键碰撞，
     * 使集成测试依赖可稳定重复运行。
     */
    @BeforeEach
    void bumpFaiSequences() {
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('fai_inspection_standard','id'), "
                + "COALESCE((SELECT MAX(id) FROM fai_inspection_standard),1)+1000)");
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('fai_inspection_standard_item','id'), "
                + "COALESCE((SELECT MAX(id) FROM fai_inspection_standard_item),1)+1000)");
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('fai_inspection_record','id'), "
                + "COALESCE((SELECT MAX(id) FROM fai_inspection_record),1)+1000)");
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('fai_change_trigger','id'), "
                + "COALESCE((SELECT MAX(id) FROM fai_change_trigger),1)+1000)");
    }


    /** 生成唯一的物料代码，避免与既有激活标准冲突。 */
    private String uniqueMaterial() {
        return "MAT" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 10).toUpperCase();
    }

    /** 创建变更触发记录，返回其 id（建单需 changeTriggerId）。 */
    private Long createChangeTrigger(String token, String materialCode, String processName) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("itemType", "PRODUCT");
        body.put("itemCode", materialCode);
        body.put("itemName", "测试产品" + materialCode);
        body.put("materialCode", materialCode);
        body.put("materialName", "测试物料" + materialCode);
        body.put("processName", processName);
        body.put("triggerType", "首次生产");
        body.put("batchNo", "B" + System.nanoTime());

        MvcResult result = mvc.perform(post("/api/v1/fai/change-triggers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = json.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    /** 用变更触发 id 建单，返回首件记录 id。 */
    private Long createInspection(String token, Long changeTriggerId) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("changeTriggerId", changeTriggerId);

        MvcResult result = mvc.perform(post("/api/v1/fai/inspections")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = json.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    /** 构建含一个参数项的保存标准请求（带标准值/上下限）。 */
    private ObjectNode buildStandardBody(String materialCode, String processName, String paramCode,
                                          String standardValue, BigDecimal upper, BigDecimal lower,
                                          Integer stdVersion, String isActive) {
        ObjectNode item = json.createObjectNode();
        item.put("paramName", "尺寸" + paramCode);
        item.put("paramCode", paramCode);
        item.put("paramCategory", "关键尺寸");
        item.put("standardValue", standardValue);
        item.put("upperLimit", upper);
        item.put("lowerLimit", lower);
        item.put("unit", "mm");
        item.put("isRequired", "是");
        item.put("sortOrder", 1);
        item.put("spcEnabled", "否");

        ArrayNode items = json.createArrayNode();
        items.add(item);

        ObjectNode body = json.createObjectNode();
        body.put("materialCode", materialCode);
        body.put("materialName", "物料" + materialCode);
        body.put("itemType", "PRODUCT");
        body.put("itemCode", materialCode);
        body.put("itemName", "产品" + materialCode);
        body.put("processName", processName);
        body.put("processCode", "ASM");
        if (stdVersion != null) {
            body.put("stdVersion", stdVersion);
        }
        body.put("isActive", isActive == null ? "是" : isActive);
        body.put("remark", "集成测试标准");
        body.set("items", items);
        return body;
    }

    // ===== M3-005 (P0)：无激活标准建单成功且 items 为空 =====

    @Test
    void m3_005_createInspection_withoutActiveStandard_succeedsWithEmptyItems() throws Exception {
        String token = login(SZ_MGR, PWD);
        String materialCode = uniqueMaterial();
        Long triggerId = createChangeTrigger(token, materialCode, "装配");

        // 该物料+工序当前无任何激活标准
        mvc.perform(get("/api/v1/fai/standards/" + materialCode + "/装配")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());

        Long faiId = createInspection(token, triggerId);

        mvc.perform(get("/api/v1/fai/inspections/" + faiId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(faiId))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    // ===== M3-006 (P0)：建单编号生成规则 =====

    @Test
    void m3_006_createInspection_faiNoMatchesRule() throws Exception {
        String token = login(SZ_MGR, PWD);
        String materialCode = uniqueMaterial();
        Long triggerId = createChangeTrigger(token, materialCode, "焊接");

        MvcResult result = mvc.perform(post("/api/v1/fai/inspections")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeTriggerId\":" + triggerId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode root = json.readTree(result.getResponse().getContentAsString());
        Long faiId = root.path("data").path("id").asLong();
        String faiNo = root.path("data").path("faiNo").asText();

        assertNotNull(faiNo);
        assertFalse(faiNo.isBlank(), "faiNo 不应为空");
        assertTrue(faiNo.matches("^FAI-(SZ|MZ)-\\d{8}-\\d{3,4}$"),
                "faiNo 应符合 FAI-{plant}-{yyyyMMdd}-NNN 规则，实际为: " + faiNo);

        // 详情接口同样返回该编号
        mvc.perform(get("/api/v1/fai/inspections/" + faiId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.faiNo").value(faiNo));
    }

    // ===== M3-012 (P1)：建单时 USL/LSL/标准值从激活标准复制 =====

    @Test
    void m3_012_createInspection_copiesStandardLimitsFromActiveStandard() throws Exception {
        String token = login(SZ_MGR, PWD);
        String materialCode = uniqueMaterial();
        String processName = "装配";
        String paramCode = "DIM_A";

        // 1) 先建激活标准（含标准值/上下限）
        BigDecimal upper = new BigDecimal("10.05");
        BigDecimal lower = new BigDecimal("9.95");
        String stdValue = "10.00";
        ObjectNode stdBody = buildStandardBody(materialCode, processName, paramCode, stdValue, upper, lower, null, "是");
        mvc.perform(post("/api/v1/fai/standards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(stdBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 2) 建单 —— 激活标准存在，建单时 inspection item 应从标准复制数值
        Long triggerId = createChangeTrigger(token, materialCode, processName);
        Long faiId = createInspection(token, triggerId);

        MvcResult result = mvc.perform(get("/api/v1/fai/inspections/" + faiId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode data = json.readTree(result.getResponse().getContentAsString()).path("data");
        ArrayNode items = (ArrayNode) data.path("items");
        assertEquals(1, items.size(), "应生成一个与标准参数对应的检验项");
        JsonNode item = items.get(0);
        assertEquals(paramCode, item.path("paramCode").asText());
        assertEquals(stdValue, item.path("standardValue").asText());
        assertEquals(0, new BigDecimal(item.path("upperLimit").asText()).compareTo(upper));
        assertEquals(0, new BigDecimal(item.path("lowerLimit").asText()).compareTo(lower));
    }

    // ===== M3-013 (P1)：标准版本递增 =====

    @Test
    void m3_013_standardVersionIncrementsOnRecreate() throws Exception {
        String token = login(SZ_MGR, PWD);
        String materialCode = uniqueMaterial();
        String processName = "焊接";
        String paramCode = "DIM_B";

        // 第一次建标准（缺省 version=1）
        ObjectNode stdBodyV1 = buildStandardBody(materialCode, processName, paramCode, "5.00",
                new BigDecimal("5.10"), new BigDecimal("4.90"), null, "是");
        MvcResult r1 = mvc.perform(post("/api/v1/fai/standards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(stdBodyV1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long stdIdV1 = json.readTree(r1.getResponse().getContentAsString()).path("data").asLong();

        // 读取 v1 版本号
        MvcResult g1 = mvc.perform(get("/api/v1/fai/standards/" + stdIdV1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        int versionV1 = json.readTree(g1.getResponse().getContentAsString()).path("data").path("stdVersion").asInt();
        assertTrue(versionV1 >= 1, "首版标准版本应 >= 1，实际: " + versionV1);

        // 第二次建同一 物料+工序 标准（激活），期望版本自增
        ObjectNode stdBodyV2 = buildStandardBody(materialCode, processName, paramCode, "5.05",
                new BigDecimal("5.15"), new BigDecimal("4.95"), null, "是");
        MvcResult r2 = mvc.perform(post("/api/v1/fai/standards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(stdBodyV2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long stdIdV2 = json.readTree(r2.getResponse().getContentAsString()).path("data").asLong();

        MvcResult g2 = mvc.perform(get("/api/v1/fai/standards/" + stdIdV2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode dataV2 = json.readTree(g2.getResponse().getContentAsString()).path("data");
        int versionV2 = dataV2.path("stdVersion").asInt();
        assertEquals(versionV1 + 1, versionV2,
                "同 物料+工序 重建标准，版本号应自增 1（v1=" + versionV1 + ", v2=" + versionV2 + "）");

        // 最新激活标准应指向 v2（标准值已被更新）
        mvc.perform(get("/api/v1/fai/standards/" + materialCode + "/" + processName)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stdVersion").value(versionV2))
                .andExpect(jsonPath("$.data.items[0].standardValue").value("5.05"));
    }

    // ===== M3-015 (P2)：refreshStandard 同步最新标准值并追加新参数项 =====

    @Test
    void m3_015_refreshStandard_syncsLatestValueAndAppendsNewParam() throws Exception {
        String token = login(SZ_MGR, PWD);
        String materialCode = uniqueMaterial();
        String processName = "装配";
        String paramCode = "DIM_C";

        // 1) 建激活标准 v1（单参数）
        ObjectNode stdBodyV1 = buildStandardBody(materialCode, processName, paramCode, "20.00",
                new BigDecimal("20.20"), new BigDecimal("19.80"), null, "是");
        mvc.perform(post("/api/v1/fai/standards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(stdBodyV1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 2) 建单（此时 inspection item 标准值 = 20.00）
        Long triggerId = createChangeTrigger(token, materialCode, processName);
        Long faiId = createInspection(token, triggerId);

        MvcResult before = mvc.perform(get("/api/v1/fai/inspections/" + faiId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        ArrayNode itemsBefore = (ArrayNode) json.readTree(before.getResponse().getContentAsString())
                .path("data").path("items");
        assertEquals(1, itemsBefore.size());
        assertEquals("20.00", itemsBefore.get(0).path("standardValue").asText());

        // 3) 查最新标准 id，构造 v2：修改原参数标准值 + 新增一个参数
        MvcResult latest = mvc.perform(get("/api/v1/fai/standards/" + materialCode + "/" + processName)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode latestData = json.readTree(latest.getResponse().getContentAsString()).path("data");
        long stdId = latestData.path("id").asLong();

        // 复用 v1 的 items，并追加新参数
        ArrayNode items = json.createArrayNode();
        ObjectNode existing = json.createObjectNode();
        existing.put("paramName", "尺寸" + paramCode);
        existing.put("paramCode", paramCode);
        existing.put("paramCategory", "关键尺寸");
        existing.put("standardValue", "21.00"); // 改值
        existing.put("upperLimit", new BigDecimal("21.20"));
        existing.put("lowerLimit", new BigDecimal("20.80"));
        existing.put("unit", "mm");
        existing.put("isRequired", "是");
        existing.put("sortOrder", 1);
        existing.put("spcEnabled", "否");
        items.add(existing);

        ObjectNode fresh = json.createObjectNode();
        fresh.put("paramName", "外观检查");
        fresh.put("paramCode", "VIS_D");
        fresh.put("paramCategory", "AQL");
        fresh.put("standardValue", "合格");
        fresh.put("unit", "");
        fresh.put("isRequired", "是");
        fresh.put("sortOrder", 2);
        fresh.put("spcEnabled", "否");
        items.add(fresh);

        ObjectNode stdBodyV2 = json.createObjectNode();
        stdBodyV2.put("id", stdId);
        stdBodyV2.put("materialCode", materialCode);
        stdBodyV2.put("materialName", "物料" + materialCode);
        stdBodyV2.put("itemType", "PRODUCT");
        stdBodyV2.put("itemCode", materialCode);
        stdBodyV2.put("itemName", "产品" + materialCode);
        stdBodyV2.put("processName", processName);
        stdBodyV2.put("processCode", "ASM");
        stdBodyV2.put("isActive", "是");
        stdBodyV2.put("remark", "改值+新增参数");
        stdBodyV2.set("items", items);

        mvc.perform(put("/api/v1/fai/standards/" + stdId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(stdBodyV2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 4) 刷新首件记录的标准值
        mvc.perform(post("/api/v1/fai/inspections/" + faiId + "/refresh-standard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 5) 校验：原项标准值已同步为 21.00，新增项已追加（共 2 项）
        MvcResult after = mvc.perform(get("/api/v1/fai/inspections/" + faiId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        ArrayNode itemsAfter = (ArrayNode) json.readTree(after.getResponse().getContentAsString())
                .path("data").path("items");
        assertEquals(2, itemsAfter.size(), "刷新后应为 2 个检验项（原项 + 标准新增项）");
        JsonNode dimItem = itemsAfter.get(0);
        assertEquals("21.00", dimItem.path("standardValue").asText(),
                "原有检验项的标准值应刷新为最新标准值 21.00");
    }
}
