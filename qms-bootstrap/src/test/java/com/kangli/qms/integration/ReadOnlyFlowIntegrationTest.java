package com.kangli.qms.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 多模块只读流程集成测试：R06 全权限账号遍历各模块列表/详情，断言 code=0（回滚安全）。
 */
class ReadOnlyFlowIntegrationTest extends BaseIntegrationTest {

    private String token;

    @BeforeEach
    void loginAsManager() throws Exception {
        token = login("sz_mgr01", "123456");
    }

    @Test
    void finishedGoods_list() throws Exception {
        mvc.perform(get("/api/v1/finished-goods").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void materialInspections_list() throws Exception {
        mvc.perform(get("/api/v1/material-inspections").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void spcProcesses_list() throws Exception {
        mvc.perform(get("/api/v1/spc/processes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void traceNodes_list() throws Exception {
        mvc.perform(get("/api/v2/incoming-trace/nodes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void traceTree_down() throws Exception {
        mvc.perform(get("/api/v2/incoming-trace/tree?rootBarcode=KL-PWR-B01-2026-001&direction=DOWN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void suppliers_list() throws Exception {
        mvc.perform(get("/api/v1/suppliers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }
}
