package com.kangli.qms.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M1-007（TODO:28 回归，P0）：来料 DELETE 端点仍暴露。
 *
 * <p>现状登记：{@code DELETE /api/v1/material-inspections/{id}} 端点目前仍然存在（见 MaterialInspectionController），
 * 调用 MyBatis-Plus 逻辑删除（isDeleted），非物理删除，符合「后端不物理删除」红线，但来料检验记录属电子记录，
 * 不应提供删除能力。TODO:28 登记：<b>待产品确认后移除该删除端点</b>（本测试不删除生产代码，遵守迁移铁律与代码红线）。
 *
 * <p>本测试断言该端点仍存在且返回结构化响应（HTTP 200 + R 体，非裸 500），用于锁定「端点未误删 + 不裸 500」，
 * 同时作为将来移除端点的回归基线。
 */
class MaterialInspectionDeleteEndpointTest extends BaseIntegrationTest {

    private static final String ADMIN = "qms_admin";
    private static final String PWD = "123456";

    /** M1-007a：DELETE 端点仍存在且返回结构化响应（非 500）。用不存在 id，避免污染真实数据。 */
    @Test
    void m1_007_deleteEndpoint_existsAndReturnsStructuredResponse() throws Exception {
        String token = login(ADMIN, PWD);

        mvc.perform(MockMvcRequestBuilders.delete("/api/v1/material-inspections/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists());
        // 不断言具体 code 值（逻辑删除不存在 id 时 MyBatis-Plus 返回 0 行，code 仍为 0）；
        // 关键断言：端点存在、HTTP 200、响应为结构化 R（$.code 存在），不再裸 500。
    }
}
