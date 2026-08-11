package com.kangli.qms.integration;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证流程集成测试（test-plan AUTH-001/002/012 + 登出黑名单）。
 */
class AuthFlowIntegrationTest extends BaseIntegrationTest {

    /** AUTH-001：正确账密登录返回 token。 */
    @Test
    void login_returnsToken() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"qms_admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    /** AUTH-002：错误密码 -> 1001。 */
    @Test
    void wrongPassword_returns1001() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"qms_admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    /** 不存在的账号 -> 1001。 */
    @Test
    void unknownAccount_returns1001() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"account\":\"no_such_user\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001));
    }

    /** /auth/me 返回当前用户信息。 */
    @Test
    void me_returnsUserInfo() throws Exception {
        String token = login("qms_admin", "123456");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").exists());
    }

    /** 登出后 token 进黑名单，访问受保护接口 -> 1007。 */
    @Test
    void logout_blacklistsToken() throws Exception {
        String token = login("qms_admin", "123456");
        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1007));
    }
}
