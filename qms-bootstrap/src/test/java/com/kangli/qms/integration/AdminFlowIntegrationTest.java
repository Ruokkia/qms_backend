package com.kangli.qms.integration;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统管理流程集成测试（test-plan ADMIN-001 等 + 权限）：账号/角色列表 + 建账号（回滚）。
 */
class AdminFlowIntegrationTest extends BaseIntegrationTest {

    @Test
    void listUsers_ok() throws Exception {
        String token = login("qms_admin", "123456");
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void listRoles_ok() throws Exception {
        String token = login("qms_admin", "123456");
        mvc.perform(get("/api/v1/admin/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 建账号（@Transactional 回滚）。 */
    @Test
    void createUser_ok() throws Exception {
        String token = login("qms_admin", "123456");
        mvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"account\":\"it_test_user\",\"realName\":\"集成测试\",\"roleCode\":\"R02\",\"plantCode\":\"SZ\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 重复账号 -> 400。 */
    @Test
    void createUser_duplicate_400() throws Exception {
        String token = login("qms_admin", "123456");
        mvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"account\":\"sz_insp01\",\"realName\":\"重复\",\"roleCode\":\"R02\",\"plantCode\":\"SZ\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
