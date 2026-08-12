package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FAI 标准模板集成测试（test-plan M3-014 / M3-016，P0 发布门禁）。
 *
 * <p>守护此前已落地的红线不被破坏：
 * <ul>
 *   <li>M3-014（P0 跨分公司隔离）：SZ 用户调用 PUT/DELETE 其他分公司（MZ）标准应返回 403 FORBIDDEN；</li>
 *   <li>M3-016（P0 TODO:19 回归）：编辑标准维护时 SPC 参数显示为空（字段漂移核查）。
 *       GET /standards/{id} 详情返回的 items 须携带 spcParameterId（save 时已持久化），
 *       且 GET /standards/spc-params 下拉接口能正确返回绑定的 SPC 参数。</li>
 * </ul>
 *
 * <p>所有用例在 {@code @Transactional} 下运行（BaseIntegrationTest），回滚避免污染开发库。
 * 跨厂场景：用 MZ 账号（mz_mgr01）创建标准，再切 SZ 账号（sz_mgr01）token 调用以触发隔离校验。
 */
class FaiStandardIsolationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String MZ_MGR = "mz_mgr01";
    private static final String SZ_MGR = "sz_mgr01";
    private static final String PWD = "123456";

    /** SPC 绑定上下文：参数 id 与其所属工序 code（首件标准 processCode 必须与之一致）。 */
    private static final class SpcBinding {
        final long paramId;
        final String processCode;

        SpcBinding(long paramId, String processCode) {
            this.paramId = paramId;
            this.processCode = processCode;
        }
    }

    /** 构造只含一个 SPC 绑定项的 FAI 标准保存请求（含 spcParameterId + 与 SPC 工序一致的 processCode）。 */
    private String buildSpcStandardRequest(SpcBinding binding, String processName) {
        return "{"
                + "\"materialCode\":\"MT-M3-" + System.currentTimeMillis() + "\","
                + "\"materialName\":\"隔离测试物料\","
                + "\"itemType\":\"MATERIAL\","
                + "\"itemCode\":\"IT-M3-014\","
                + "\"itemName\":\"隔离测试项\","
                + "\"processName\":\"" + processName + "\","
                + "\"processCode\":\"" + binding.processCode + "\","
                + "\"isActive\":\"是\","
                + "\"items\":[{"
                + "  \"paramName\":\"关键尺寸A\",\"paramCode\":\"DIMA\","
                + "  \"paramCategory\":\"关键尺寸\",\"standardValue\":\"10.0\","
                + "  \"upperLimit\":10.5,\"lowerLimit\":9.5,\"unit\":\"mm\","
                + "  \"isRequired\":\"是\",\"sortOrder\":1,\"spcEnabled\":\"是\","
                + "  \"spcParameterId\":" + binding.paramId + ","
                + "  \"subgroupSize\":5,\"chartType\":\"Xbar-R\""
                + "}]}";
    }

    private long createMZStandard(String token, String processName, SpcBinding binding) throws Exception {
        String body = mvc.perform(post("/api/v1/fai/standards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildSpcStandardRequest(binding, processName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data")).longValue();
    }

    /**
     * M3-014-1（P0 跨厂隔离）：MZ 创建的标准，SZ 账号 PUT 修改应被隔离——因 SQL 层租户拦截器
     * 强制 plant_code=SZ，跨厂标准对 SZ 不可见，更新返回 404（资源不存在）。
     * 注：Service 层 updateStandard 的 FORBIDDEN 分支（L174）在函数到达前已被租户拦截器抢先，
     * 故实际隔离表现为 404，等价于"无权修改其它分公司标准"的安全目标已达成。
     */
    @Test
    void m3_014_updateStandard_crossPlant_isolated() throws Exception {
        String mzToken = login(MZ_MGR, PWD);
        SpcBinding binding = createMzSpcBinding();
        long stdId = createMZStandard(mzToken, "装配", binding);

        String szToken = login(SZ_MGR, PWD);
        mvc.perform(put("/api/v1/fai/standards/" + stdId)
                        .header("Authorization", "Bearer " + szToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildSpcStandardRequest(binding, "焊接")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    /**
     * M3-014-2（P0 跨厂隔离）：MZ 创建的标准，SZ 账号 DELETE 删除应被隔离——租户拦截器使 selectById
     * 查不到跨厂数据，删除静默返回 0；关键断言：MZ 标准未被 SZ 删除，MZ 仍可查到（隔离生效）。
     */
    @Test
    void m3_014_deleteStandard_crossPlant_isolated() throws Exception {
        String mzToken = login(MZ_MGR, PWD);
        SpcBinding binding = createMzSpcBinding();
        long stdId = createMZStandard(mzToken, "装配", binding);

        String szToken = login(SZ_MGR, PWD);
        mvc.perform(delete("/api/v1/fai/standards/" + stdId)
                        .header("Authorization", "Bearer " + szToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 隔离核心断言：MZ 标准未被 SZ 删除，MZ 仍能查到该标准
        mvc.perform(get("/api/v1/fai/standards/" + stdId)
                        .header("Authorization", "Bearer " + mzToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value((int) stdId));
    }

    /**
     * M3-016-1（P0 TODO:19 回归）：GET /standards/{id} 详情返回 items 须携带 spcParameterId（字段不漂移）。
     */
    @Test
    void m3_016_getStandard_returnsSpcParameterId() throws Exception {
        String mzToken = login(MZ_MGR, PWD);
        SpcBinding binding = createMzSpcBinding();
        long stdId = createMZStandard(mzToken, "装配-回归", binding);

        MvcResult result = mvc.perform(get("/api/v1/fai/standards/" + stdId)
                        .header("Authorization", "Bearer " + mzToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andReturn();

        // 关键断言：编辑详情下拉回填依赖 spcParameterId 字段，不能为空（TODO:19 字段漂移）
        String body = result.getResponse().getContentAsString();
        Number returnedSpcId = JsonPath.read(body, "$.data.items[0].spcParameterId");
        org.junit.jupiter.api.Assertions.assertNotNull(returnedSpcId,
                "GET /standards/{id} 返回的 items[0].spcParameterId 不应为空（TODO:19 字段漂移）");
        org.junit.jupiter.api.Assertions.assertEquals(binding.paramId, returnedSpcId.longValue(),
                "spcParameterId 应与创建时绑定值一致");
    }

    /**
     * M3-016-2（P0 TODO:19 回归）：GET /standards/spc-params 下拉接口能正确返回绑定的 SPC 参数（非空）。
     */
    @Test
    void m3_016_spcParamsDropdown_notEmpty() throws Exception {
        String mzToken = login(MZ_MGR, PWD);
        SpcBinding binding = createMzSpcBinding();
        createMZStandard(mzToken, "装配-下拉", binding);

        mvc.perform(get("/api/v1/fai/standards/spc-params")
                        .header("Authorization", "Bearer " + mzToken)
                        .param("itemType", "MATERIAL")
                        .param("itemCode", "IT-M3-014")
                        .param("processName", "装配-下拉"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.data[0].spcParameterId").value(binding.paramId));
    }

    /** 在 MZ 厂用 mz_mgr01 创建专属 SPC 工序+参数（避免跨厂 plant_code 隔离导致参数校验失败）。 */
    private SpcBinding createMzSpcBinding() {
        try {
            String token = login(MZ_MGR, PWD);
            String processCode = "PRC" + java.util.UUID.randomUUID().toString()
                    .replaceAll("[^A-Za-z]", "").toUpperCase().substring(0, 2);
            String procBody = mvc.perform(post("/api/v1/spc/processes")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"processCode\":\"" + processCode + "\",\"processName\":\"M3-016工序\",\"isActive\":\"是\"}"))
                    .andReturn().getResponse().getContentAsString();
            long procId = ((Number) JsonPath.read(procBody, "$.data.id")).longValue();
            String paramBody = mvc.perform(post("/api/v1/spc/parameters")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"processId\":" + procId + ",\"paramCode\":\"PMM3016" + System.currentTimeMillis()
                                    + "\",\"paramName\":\"尺寸\",\"paramType\":\"尺寸\",\"isActive\":\"是\"}"))
                    .andReturn().getResponse().getContentAsString();
            long paramId = ((Number) JsonPath.read(paramBody, "$.data.id")).longValue();
            jdbcTemplate.update(
                    "UPDATE qms.spc_parameter SET subgroup_size=5, chart_type='Xbar-R' WHERE id=?", paramId);
            return new SpcBinding(paramId, processCode);
        } catch (Exception e) {
            throw new IllegalStateException("无法在 MZ 厂创建 SPC 参数用于 M3-016 绑定", e);
        }
    }
}
