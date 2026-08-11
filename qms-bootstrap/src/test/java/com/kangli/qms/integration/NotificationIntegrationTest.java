package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通知归属集成测试（test-plan NOTIF-002/003）：标记他人通知应 403，标记自己的应成功。
 */
class NotificationIntegrationTest extends BaseIntegrationTest {

    private long createNotification(String token, Long targetUserId) throws Exception {
        String body = mvc.perform(post("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"userId\":" + targetUserId + ",\"type\":\"EXCEPTION\",\"title\":\"集成测试通知\",\"content\":\"测试内容\",\"level\":\"提醒\",\"plantCode\":\"SZ\"}"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** NOTIF-002：标记他人通知已读 -> 403（归属校验）。 */
    @Test
    void markRead_othersNotification_403() throws Exception {
        String mgr = login("sz_mgr01", "123456");
        Long inspId = loginUserId("sz_insp01", "123456");
        long nid = createNotification(mgr, inspId);
        mvc.perform(post("/api/v1/notifications/" + nid + "/read").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    /** NOTIF-003：标记自己的通知已读 -> 0。 */
    @Test
    void markRead_own_ok() throws Exception {
        String mgr = login("sz_mgr01", "123456");
        Long inspId = loginUserId("sz_insp01", "123456");
        long nid = createNotification(mgr, inspId);
        String insp = login("sz_insp01", "123456");
        mvc.perform(post("/api/v1/notifications/" + nid + "/read").header("Authorization", "Bearer " + insp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 通知不存在 -> 404。 */
    @Test
    void markRead_missing_404() throws Exception {
        String mgr = login("sz_mgr01", "123456");
        mvc.perform(post("/api/v1/notifications/999999/read").header("Authorization", "Bearer " + mgr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}
