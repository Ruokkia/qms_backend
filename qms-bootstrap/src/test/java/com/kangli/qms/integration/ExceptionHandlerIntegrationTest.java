package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局异常兜底集成测试（test-plan XC-032 / D16）。
 * 借助测试辅助端点 /api/v1/__test__/boom 稳定触发未捕获 RuntimeException，
 * 验证 GlobalExceptionHandler 兜底分支返回 code=500 且不向响应透传堆栈/异常类名。
 */
class ExceptionHandlerIntegrationTest extends BaseIntegrationTest {

    /** XC-032：未捕获 Exception -> HTTP 500（@ResponseStatus 兜底）+ R.code=500，且 message 不含堆栈特征。 */
    @Test
    void uncaughtException_returns500_noStackTrace() throws Exception {
        String token = login("qms_admin", "123456");
        MvcResult result = mvc.perform(get("/api/v1/exceptions/__boom__")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertEquals(500, (int) JsonPath.read(body, "$.code"));
        String msg = JsonPath.read(body, "$.message");
        // 兜底不应透传异常类名或堆栈帧
        assertFalse(msg.contains("RuntimeException"), "message 不应包含异常类名");
        assertFalse(msg.contains("boom-unittest-trigger"), "message 不应包含触发字符串");
        assertFalse(msg.contains("at com.kangli"), "message 不应包含堆栈帧");
        assertFalse(msg.contains("java.lang"), "message 不应包含 java 包路径");
        // traceId 仍应填充（统一响应契约）
        assertTrue(JsonPath.read(body, "$.traceId") != null);
    }
}
