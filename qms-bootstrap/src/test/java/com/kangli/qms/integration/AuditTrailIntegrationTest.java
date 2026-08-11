package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计追溯集成测试：建单 + 发起流程后，audit-trail 应聚合出审计记录。
 */
class AuditTrailIntegrationTest extends BaseIntegrationTest {

    @Test
    void auditTrail_recordsExceptionFlow() throws Exception {
        String token = login("sz_mgr01", "123456");
        // 建单
        String body = mvc.perform(post("/api/v1/exceptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"sourceType\":\"手动录入\",\"severity\":\"一般\",\"defectDesc\":\"审计测试\",\"materialCode\":\"AT-MAT\",\"plantCode\":\"SZ\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        // 发起 CAPA 流程
        mvc.perform(post("/api/v1/exceptions/" + id + "/initiate")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"processType\":\"CAPA\",\"d0Symptom\":\"审计D0\",\"d0Initiator\":\"测试经理\"}"))
                .andExpect(status().isOk());
        // 审计追溯应含记录（至少发起流程的 CREATE/INITIATE）
        mvc.perform(get("/api/v1/exceptions/" + id + "/audit-trail")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }
}
