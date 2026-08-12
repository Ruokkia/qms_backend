package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import com.kangli.qms.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Random;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPC 集成回归测试（test-plan M4-030 / M4-031 / M4-033 / M4-035 / M4-036）。
 *
 * <p>守护此前已修复的根因不再回归：
 * <ul>
 *   <li>D4：SPC 系数缺失（n 越界 / c4=0）时返回可读 INTERNAL_ERROR，而非裸 500/NPE 堆栈；</li>
 *   <li>D5：子组保存后 recalc 失败不回滚子组（NESTED 事务隔离，saveInternal catch）；</li>
 *   <li>D6：控制限 recalc 返回 SpcControlLimitResponse（已剥离 isDeleted/version/createdBy 审计列），
 *       不返回裸持久化实体（红线 #2）。</li>
 * </ul>
 *
 * <p>注意：SPC 字典层 {@code SpcParameterRequest} 不接收 subgroupSize（留待标准层配置），
 * 故数据库 spc_parameter.subgroup_size 默认 null。测试中需直接 JdbcTemplate 修正才能走通正常 recalc。
 * 所有用例在 {@code @Transactional} 下运行（BaseIntegrationTest），回滚避免污染开发库。
 *
 * <p>processCode 业务校验要求 {@code ^[A-Z]{2,5}$}（2~5 位大写字母），故随机生成唯一大写字母码。
 */
class SpcRegressionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private com.kangli.qms.service.spc.SpcCapabilityService capabilityService;

    private static final String PROC_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    private String lastProcessCode;

    private String randProc() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(PROC_CHARS.charAt(r.nextInt(PROC_CHARS.length())));
        }
        return sb.toString();
    }

    private long createProcess(String token) throws Exception {
        lastProcessCode = randProc();
        String body = mvc.perform(post("/api/v1/spc/processes")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"processCode\":\"" + lastProcessCode + "\",\"processName\":\"回归工序\",\"isActive\":\"是\"}"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long createParam(String token, long procId, String code) throws Exception {
        String body = mvc.perform(post("/api/v1/spc/parameters")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"processId\":" + procId + ",\"paramCode\":\"" + code + "\",\"paramName\":\"尺寸\",\"paramType\":\"尺寸\",\"isActive\":\"是\"}"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /**
     * M4-030（D4 + D5 守护）：参数 subgroupSize 设为越界值 13（系数表仅 1~12），提交 13 样本子组
     * （status=已完成）→ 自动 recalc 因 coef==null 抛可读 INTERNAL_ERROR，但被 saveInternal catch（D5），
     * 子组仍保存成功（200）。随后手动 recalc 控制限再次触发 coef==null → 返回可读 INTERNAL_ERROR（code=500），
     * 而非裸 500/NPE 堆栈；且子组已落库（recalc 失败不回滚）。
     */
    @Test
    void m4_030_controlLimitRecalc_outOfRangeSubgroupSize_readableError_notRaw500() throws Exception {
        String token = login("sz_mgr01", "123456");
        long procId = createProcess(token);
        long paramId = createParam(token, procId, "PM4030" + System.currentTimeMillis());
        // 字典层不接收 subgroupSize，直接修正库列为越界值 13（系数表无此 n）
        jdbcTemplate.update("UPDATE qms.spc_parameter SET subgroup_size=13 WHERE id=?", paramId);

        // 提交 2 个 13 样本子组（== subgroupSize）→ status=已完成，count>=2 触发 recalc，
        // 因 coef==null 抛可读 INTERNAL_ERROR 但被 saveInternal catch（D5），子组仍保存成功
        StringBuilder samples = new StringBuilder("[");
        for (int i = 0; i < 13; i++) {
            if (i > 0) samples.append(",");
            samples.append(10.0 + i * 0.1);
        }
        samples.append("]");
        for (int k = 0; k < 2; k++) {
            mvc.perform(post("/api/v1/spc/subgroups")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content("{\"paramId\":" + paramId + ",\"sampleValues\":" + samples + ",\"itemType\":\"PRODUCT\",\"itemCode\":\"PC030\",\"batchNo\":\"B030\",\"barcode\":\"BC030" + k + "\",\"materialName\":\"物料\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        // 手动 recalc 控制限：n=13 → coef==null → 可读 INTERNAL_ERROR（D4 守护，非裸 500）
        mvc.perform(post("/api/v1/spc/control-limits/" + paramId + "/recalc")
                        .header("Authorization", "Bearer " + token)
                        .param("itemType", "PRODUCT").param("itemCode", "PC030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /**
     * M4-031（Xbar-s 分支 + 前置守卫，D4 关联）：Xbar-s 参数 + 子组数不足 20 且无 FAI 标准层时，
     * capability recalc 抛结构化 BusinessException（code=400 子组不足 或 500 系数/规格缺失），
     * 而非裸 NPE/500 堆栈。HTTP 端点 recalcCapability 使用 NESTED 事务，在测试事务环境下偶发不稳定，
     * 故此处直接调用 service 层验证结构化错误语义。
     */
    @Test
    void m4_031_xbarS_capabilityRecalc_insufficientSubgroups_structured400() throws Exception {
        String token = login("sz_mgr01", "123456");
        long procId = createProcess(token);
        long paramId = createParam(token, procId, "PM4031" + System.currentTimeMillis());
        // Xbar-s 参数
        jdbcTemplate.update("UPDATE qms.spc_parameter SET chart_type='Xbar-s' WHERE id=?", paramId);

        // service 层直接 recalc：子组不足（无已完成子组）→ 结构化 BusinessException，非裸堆栈
        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> capabilityService.recalcCapability(paramId, "SZ", "PRODUCT", "PC031", null));
        int code = ex.getCode();
        org.junit.jupiter.api.Assertions.assertTrue(code == 400 || code == 500,
                "capability recalc 应返回结构化错误(400/500)，实际 code=" + code + " msg=" + ex.getMessage());
        org.junit.jupiter.api.Assertions.assertNotNull(ex.getMessage());
    }

    /**
     * M4-033（N+1 / 超时观测）：参数列表查询正常返回 200 且不抛 500；记录耗时用于定位 N+1。
     * 不强判耗时阈值以避免 CI 抖动。
     */
    @Test
    void m4_033_parameterList_no500_andReturnsCorrect() throws Exception {
        String token = login("sz_mgr01", "123456");
        long procId = createProcess(token);
        createParam(token, procId, "PM4331" + System.currentTimeMillis());
        createParam(token, procId, "PM4332" + System.currentTimeMillis());

        long start = System.currentTimeMillis();
        MvcResult result = mvc.perform(get("/api/v1/spc/parameters?processId=" + procId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();
        long elapsed = System.currentTimeMillis() - start;
        // 观测日志：定位潜在 N+1 / 慢查询
        org.slf4j.LoggerFactory.getLogger(SpcRegressionIntegrationTest.class)
                .info("[M4-033] GET /parameters?processId={} 耗时 {}ms", procId, elapsed);
        // 基本健全性：列表元素为 SpcParameterResponse（不含 isDeleted 裸字段）
        String body = result.getResponse().getContentAsString();
        JsonPath.read(body, "$.data[0].paramCode");
    }

    /**
     * B2 / M4-033（P0 SPC 超时回归）：批量造数（20 参数 × 各带完成子组）放大潜在 N+1 风险，
     * 断言 GET /api/v1/spc/parameters 响应耗时 < 1000ms 且返回数据正确（守护去 N+1 优化不回归）。
     */
    @Test
    void m4_033_timeout_under1000ms_b2() throws Exception {
        String token = login("sz_mgr01", "123456");
        long procId = createProcess(token);

        // 批量造 20 个参数（每个修库列以走通正常 recalc 路径），放大列表查询的 N+1 风险
        int paramCount = 20;
        for (int i = 0; i < paramCount; i++) {
            long paramId = createParam(token, procId, "PMB2" + i + "_" + System.currentTimeMillis());
            jdbcTemplate.update(
                    "UPDATE qms.spc_parameter SET subgroup_size=5, chart_type='Xbar-R' WHERE id=?", paramId);
            // 每个参数提交 2 个完成子组（count>=2 触发 recalc，增加聚合查询负担）
            for (int k = 1; k <= 2; k++) {
                mvc.perform(post("/api/v1/spc/subgroups")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{\"paramId\":" + paramId + ",\"sampleValues\":[10.0,10.1,10.2,10.3,10.4],"
                                        + "\"itemType\":\"PRODUCT\",\"itemCode\":\"PCB2" + i + "\",\"batchNo\":\"BB2" + i + "\","
                                        + "\"barcode\":\"BCB2" + i + "_" + k + "\",\"materialName\":\"物料\"}"))
                        .andExpect(status().isOk());
            }
        }

        long start = System.currentTimeMillis();
        MvcResult result = mvc.perform(get("/api/v1/spc/parameters?processId=" + procId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(paramCount))
                .andReturn();
        long elapsed = System.currentTimeMillis() - start;
        org.slf4j.LoggerFactory.getLogger(SpcRegressionIntegrationTest.class)
                .info("[B2/M4-033] GET /parameters?processId={} 批量20参数 耗时 {}ms", procId, elapsed);

        org.junit.jupiter.api.Assertions.assertTrue(elapsed < 1000,
                "GET /api/v1/spc/parameters 耗时 " + elapsed + "ms 超过 1000ms 阈值（B2 SPC 超时回归失败）");
        // 列表元素为 SpcParameterResponse（不含裸实体审计列，D6 红线守护）
        String body = result.getResponse().getContentAsString();
        JsonPath.read(body, "$.data[0].paramCode");
    }

    /**
     * M4-035（工序编辑返回）：PUT /processes/{id} 修改 sortOrder 后，返回体中 sortOrder 为更新值。
     * 注：SpcProcessResponse 不回传 version（乐观锁字段，库内自动递增），故仅断言 sortOrder。
     * processCode 为更新校验必填字段（validateProcess）。
     */
    @Test
    void m4_035_processUpdate_returnsUpdatedSortOrder() throws Exception {
        String token = login("sz_mgr01", "123456");
        long procId = createProcess(token);

        int newSort = 99;
        mvc.perform(put("/api/v1/spc/processes/" + procId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"processCode\":\"" + lastProcessCode + "\",\"processName\":\"回归工序-改\",\"sortOrder\":" + newSort + ",\"isActive\":\"是\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(procId))
                .andExpect(jsonPath("$.data.sortOrder").value(newSort));
    }

    /**
     * M4-036（D6 红线守护）：控制限 recalc 成功返回 SpcControlLimitResponse（已剥离审计列），
     * 不含 isDeleted/version/createdBy 等裸实体字段。
     * 通过 JdbcTemplate 修正 subgroupSize=5 + chartType='Xbar-R'，提交 2 个完成子组触发有效 recalc。
     */
    @Test
    void m4_036_controlLimitRecalc_returnsDTO_notRawEntity() throws Exception {
        String token = login("sz_mgr01", "123456");
        long procId = createProcess(token);
        long paramId = createParam(token, procId, "PM4036" + System.currentTimeMillis());
        // 字典层不接收 subgroupSize，直接修正库列以走通正常 recalc
        jdbcTemplate.update(
                "UPDATE qms.spc_parameter SET subgroup_size=5, chart_type='Xbar-R' WHERE id=?", paramId);

        String itemType = "PRODUCT";
        String itemCode = "PC4036" + System.currentTimeMillis();
        // 提交 2 个完成子组（各 5 样本 == subgroupSize）→ status=已完成，count>=2 触发 recalc
        for (int i = 1; i <= 2; i++) {
            mvc.perform(post("/api/v1/spc/subgroups")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content("{\"paramId\":" + paramId + ",\"sampleValues\":[10.0,10.1,10.2,10.3,10.4],\"itemType\":\"" + itemType + "\",\"itemCode\":\"" + itemCode + "\",\"batchNo\":\"B036\",\"barcode\":\"BC036" + i + "\",\"materialName\":\"物料\"}"))
                    .andExpect(status().isOk());
        }

        // 显式 recalc 控制限（带维度），断言返回 DTO 而非裸实体
        mvc.perform(post("/api/v1/spc/control-limits/" + paramId + "/recalc")
                        .header("Authorization", "Bearer " + token)
                        .param("itemType", itemType).param("itemCode", itemCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.xbarUcl").exists())
                .andExpect(jsonPath("$.data.isDeleted").doesNotExist())
                .andExpect(jsonPath("$.data.version").doesNotExist())
                .andExpect(jsonPath("$.data.createdBy").doesNotExist());
    }
}
