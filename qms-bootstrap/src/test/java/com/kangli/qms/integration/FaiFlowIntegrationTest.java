package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FAI 首件检验流程集成测试（M3 + L78 回归）：建变更触发 -> 建检验单（L78 修复后不再 500）-> 详情含标准项。
 */
class FaiFlowIntegrationTest extends BaseIntegrationTest {

    @Test
    void createTriggerInspectionAndLoadItems() throws Exception {
        String token = login("sz_mgr01", "123456");
        // 建变更触发
        String body = mvc.perform(post("/api/v1/fai/change-triggers")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"triggerType\":\"物料变更\",\"itemType\":\"MATERIAL\",\"itemCode\":\"10.09.001.001\",\"materialCode\":\"10.09.001.001\",\"processName\":\"装配\",\"materialName\":\"IT-FAI\",\"batchNo\":\"IT-FAI-BATCH\"}"))
                .andReturn().getResponse().getContentAsString();
        long ctId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        // 建检验单（L78 修复：不再因 item_type/item_code/item_name 列缺失 500）
        body = mvc.perform(post("/api/v1/fai/inspections")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"changeTriggerId\":" + ctId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        long insId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        // 详情应含标准项（从激活标准复制）
        mvc.perform(get("/api/v1/fai/inspections/" + insId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    /** M3-004：建检验单时变更触发不存在 → 业务返回 code=404（R 体中表达，HTTP 200） */
    @Test
    void createInspection_withNonExistentTrigger_returns404() throws Exception {
        String token = login("sz_mgr01", "123456");
        mvc.perform(post("/api/v1/fai/inspections")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"changeTriggerId\":99999999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** M3-007：submitItems 传入非法 item id（不存在 / faiRecordId 不匹配）→ 静默跳过不报错，返回 200 */
    @Test
    void submitItems_withInvalidItemId_silentlySkips() throws Exception {
        String token = login("sz_mgr01", "123456");
        // 建变更触发 + 检验单
        String body = mvc.perform(post("/api/v1/fai/change-triggers")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"triggerType\":\"物料变更\",\"itemType\":\"MATERIAL\",\"itemCode\":\"10.09.001.002\",\"materialCode\":\"10.09.001.002\",\"processName\":\"装配\",\"materialName\":\"IT-FAI2\",\"batchNo\":\"IT-FAI2-BATCH\"}"))
                .andReturn().getResponse().getContentAsString();
        long ctId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        body = mvc.perform(post("/api/v1/fai/inspections")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"changeTriggerId\":" + ctId + "}"))
                .andReturn().getResponse().getContentAsString();
        long insId = ((Number) JsonPath.read(body, "$.data.id")).longValue();

        // 提交一个不存在的 item id（且 faiRecordId 不匹配），应静默跳过、不抛异常
        mvc.perform(put("/api/v1/fai/inspections/" + insId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"faiRecordId\":" + insId + ",\"items\":[{\"id\":99999999,\"actualValue\":12.5}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
