package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hamcrest.Matchers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 横切基础设施集成测试（test-plan XC-040~043）：分页截断 / 逻辑删除 / 参数校验。
 * 回滚安全，不依赖建单（规避 L108）。
 */
class InfrastructureIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        token = login("qms_admin", "123456");
    }

    /** XC-040：分页 size=500 应被 setMaxLimit(100) 截断（实际返回 size <= 100）。 */
    @Test
    void pagination_size500_cappedAt100() throws Exception {
        mvc.perform(get("/api/v1/material-inspections?page=1&size=500")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.size").value(Matchers.lessThanOrEqualTo(100)));
    }

    /** XC-042：逻辑删除后列表不可见（建工序 -> 删 -> 列表不含，@Transactional 回滚）。 */
    @Test
    void logicalDelete_spcProcess_invisible() throws Exception {
        String body = mvc.perform(post("/api/v1/spc/processes")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"processCode\":\"ITPA\",\"processName\":\"装配\",\"isActive\":\"是\"}"))
                .andReturn().getResponse().getContentAsString();
        long pid = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        mvc.perform(delete("/api/v1/spc/processes/" + pid)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        String list = mvc.perform(get("/api/v1/spc/processes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        assertFalse(list.contains("ITP2"), "逻辑删除后列表不应含 ITP2");
    }

    /** XC-030：@Valid 校验失败 -> 400（initiate processType 必填）。 */
    @Test
    void validation_initiateMissingProcessType_400() throws Exception {
        mvc.perform(post("/api/v1/exceptions/24/initiate")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"d0Symptom\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    /** @Valid 校验失败 -> 400（login password 必填）。 */
    @Test
    void validation_loginMissingPassword_400() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"qms_admin\"}"))
                .andExpect(status().isBadRequest());
    }
}
