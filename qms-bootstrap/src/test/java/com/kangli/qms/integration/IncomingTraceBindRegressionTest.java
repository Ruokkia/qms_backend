package com.kangli.qms.integration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M0-010（TODO:23-24 回归，P0）：绑定传非数字 ID「无反应」。
 *
 * <p>历史缺陷：早期绑定端点用 controller {@code instanceof Number} 解析 materialInspectionId/finishedGoodsInspectionId，
 * 非数字 ID 当 null 抛异常导致前端「无反应」（裸 500 / 无响应）。当前追溯体系已改为条码编码 ID，
 * {@link com.kangli.qms.service.trace.IncomingTraceService#node} 支持 {@code mi:{barcode}} / {@code fg:{barcode}}
 * 字符串 ID，并对非法 type / 不存在节点抛 {@code IllegalArgumentException}，由 GlobalExceptionHandler 统一转为结构化 {@code R{code=400}}。
 *
 * <p>本测试复现「非数字 ID」场景，断言不再裸 500 / 无反应，而是返回结构化 HTTP 400 + {@code $.code=400}。
 * 注意：GlobalExceptionHandler 将 {@code IllegalArgumentException} 映射为 HTTP 400（R 体 code=400），并非 200，
 * 因此断言 HTTP status 为 400，重点是「结构化错误、非裸 500」。
 */
class IncomingTraceBindRegressionTest extends BaseIntegrationTest {

    private static final String ADMIN = "qms_admin";
    private static final String PWD = "123456";

    /** M0-010a：非数字条码编码 ID（mi:NOT_EXIST_BARCODE）查询追溯节点，返回结构化 400「节点不存在」而非裸 500/无反应。 */
    @Test
    void m0_010_node_nonNumericBarcode_returns400() throws Exception {
        String token = login(ADMIN, PWD);

        // 用确定不存在的条码，确保返回「节点不存在」而非依赖具体数据
        mvc.perform(MockMvcRequestBuilders.get("/api/v2/incoming-trace/nodes/mi:NOT_EXIST_BARCODE")
                        .param("type", "mi")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("节点不存在")));
    }

    /** M0-010b：非法 type 参数（type=XYZ），返回结构化 400「不支持的 type」而非裸 500。 */
    @Test
    void m0_010_node_invalidType_returns400() throws Exception {
        String token = login(ADMIN, PWD);

        mvc.perform(MockMvcRequestBuilders.get("/api/v2/incoming-trace/nodes/mi:abc123")
                        .param("type", "XYZ")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("不支持的 type")));
    }
}
