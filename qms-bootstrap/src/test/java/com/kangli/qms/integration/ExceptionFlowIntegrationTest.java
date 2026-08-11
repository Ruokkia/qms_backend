package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import com.kangli.qms.domain.exception.entity.Exception8d;
import com.kangli.qms.domain.exception.mapper.Exception8dMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 异常/8D 流程集成测试（test-plan M2-020/024/025 + XC-021 + 闭环前置 + M2-026）。
 * 走真实拦截器链 + DB(qms)；@Transactional 回滚建单/发起改动。
 */
class ExceptionFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private Exception8dMapper exception8dMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 建异常单 + 发起 8D 流程，返回异常单 id。 */
    private long create8DExceptionAndInitiate(String token) throws Exception {
        String body = mvc.perform(post("/api/v1/exceptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"sourceType\":\"手动录入\",\"severity\":\"严重\",\"defectDesc\":\"集成测试异常\",\"materialCode\":\"IT-MAT-01\",\"plantCode\":\"SZ\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        mvc.perform(post("/api/v1/exceptions/" + id + "/initiate")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"processType\":\"8D\",\"d1Team\":\"集成测试团队\",\"d0Symptom\":\"集成测试D0\",\"d0Initiator\":\"测试经理\"}"));
        return id;
    }

    /**
     * M2-026：异常单「待发起」期间 8D 已被软删除，发起（processType=8D）应恢复为 D1，
     * 清空 D2-D8 历史字段，逻辑删除标志翻转回 0。
     */
    @Test
    void m2_026_softDeleted8DRestoredOnReinitiate() throws Exception {
        String token = login("sz_mgr01", "123456");
        // 1) 建异常单（待发起）
        String body = mvc.perform(post("/api/v1/exceptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"sourceType\":\"手动录入\",\"severity\":\"严重\",\"defectDesc\":\"集成测试异常\",\"materialCode\":\"IT-MAT-01\",\"plantCode\":\"SZ\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();

        // 2) 在发起前直接插入一条已软删的 8D 记录（含 D2-D8 历史值）
        jdbcTemplate.update("INSERT INTO qms.exception_8d (exception_id, current_step, plant_code, plant_name,"
                + " d2_problem_desc, d3_containment, d4_root_cause, is_deleted, version)"
                + " VALUES (?, 'D2', 'SZ', '深圳', '历史D2', '历史D3', '历史D4', 1, 1)", id);

        // 3) 首次发起 8D 流程 -> ensureEightDInitialized 命中软删恢复分支
        mvc.perform(post("/api/v1/exceptions/" + id + "/initiate")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"processType\":\"8D\",\"d1Team\":\"新团队\",\"d0Symptom\":\"复测D0\",\"d0Initiator\":\"测试经理\"}"))
                .andExpect(status().isOk());

        Exception8d restored = exception8dMapper.selectByExceptionIdIgnoreDeleted(id);
        assertEquals((short) 0, restored.getIsDeleted());
        assertEquals("D1", restored.getCurrentStep());
        assertNull(restored.getD2ProblemDesc());
        assertNull(restored.getD3Containment());
        assertNull(restored.getD4RootCause());
    }

    /** M2-020：D1 阶段不可直接 nextStep（团队提交门禁）-> 400。 */
    @Test
    void r06_d1NextStepRejectedByTeamSubmitGate() throws Exception {
        String token = login("sz_mgr01", "123456");
        long id = create8DExceptionAndInitiate(token);
        mvc.perform(post("/api/v1/exceptions/" + id + "/eight-d/next-step")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /** M2-025：8D saveOrUpdate version 不匹配 -> 2008（乐观锁）。 */
    @Test
    void eightDVersionConflict_returns2008() throws Exception {
        String token = login("sz_mgr01", "123456");
        long id = create8DExceptionAndInitiate(token);
        mvc.perform(put("/api/v1/exceptions/" + id + "/eight-d")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"currentStep\":\"D1\",\"d1Team\":\"团队\",\"version\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2008));
    }

    /** XC-021 细粒度：R02 调 8D 推进 -> 403。 */
    @Test
    void r02_cannotStep8D() throws Exception {
        String mgr = login("sz_mgr01", "123456");
        long id = create8DExceptionAndInitiate(mgr);
        String r02 = login("sz_insp01", "123456");
        mvc.perform(post("/api/v1/exceptions/" + id + "/eight-d/next-step")
                        .header("Authorization", "Bearer " + r02))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    /** M2-004：闭环前置不满足 -> 2001 CLOSE_PRECONDITION_NOT_MET。 */
    @Test
    void close_preconditionsNotMet_returns2001() throws Exception {
        String token = login("sz_mgr01", "123456");
        long id = create8DExceptionAndInitiate(token);
        mvc.perform(post("/api/v1/exceptions/" + id + "/close")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"closeReason\":\"集成测试闭环\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001));
    }
}
