package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 来料 import 自动建单集成测试（test-plan M2-041）：批量导入一条不合格来料 -> 自动建异常单。
 * 注：import 用 REQUIRES_NEW 独立提交，@Transactional 不回滚，会真实落库（开发环境接受）。
 */
class IncomingFlowIntegrationTest extends BaseIntegrationTest {

    @Test
    void importUnqualified_autoCreatesException() throws Exception {
        String token = login("qms_admin", "123456");
        String body = mvc.perform(post("/api/v1/material-inspections/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"list\":[{\"recordNo\":\"IT-IMP-001\",\"inspectionResult\":\"不合格\",\"materialCode\":\"MC-IMP\",\"materialName\":\"导入物料\",\"supplierCode\":\"SUP-SZ-02\",\"supplierName\":\"华南电路\",\"materialBatchNo\":\"IT-IMP-LOT\",\"inspectionDate\":\"2026-07-31\",\"submittedQty\":100,\"unqualifiedQty\":15,\"qualifiedQty\":85,\"unit\":\"PCS\",\"defectDesc\":\"集成导入\",\"plantCode\":\"SZ\"}],\"autoCreateException\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.createdExceptionCount").value(1))
                .andReturn().getResponse().getContentAsString();
        // 自动建出的异常单可查（走 createFromMaterialInspection 链路）
        long exId = ((Number) JsonPath.read(body, "$.data.createdExceptionIds[0]")).longValue();
        mvc.perform(get("/api/v1/exceptions/" + exId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
