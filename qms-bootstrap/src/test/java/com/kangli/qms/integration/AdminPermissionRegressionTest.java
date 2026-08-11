package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADMIN-011（TODO:9 回归，P0）：系统管理员配置用户/角色权限报 500。
 *
 * <p>历史缺陷：以系统管理员身份通过 {@code PUT /api/v1/admin/roles/{roleCode}/permissions} 配置权限时，
 * 对 R00 锁定 / R06 归一化 / 畸形参数缺少结构化校验，导致裸 500。当前实现已用
 * {@code BusinessException(BAD_REQUEST)} 在 AdminServiceImpl 全量拦截，并由 GlobalExceptionHandler 统一转
 * 为 {@code R{code=400}}。本测试复现各违规场景，断言不再裸 500（HTTP 200 且 $.code 为 0 或 400）。
 */
class AdminPermissionRegressionTest extends BaseIntegrationTest {

    private static final String ADMIN = "qms_admin";
    private static final String PWD = "123456";

    /** 取当前角色权限的 version，避免 updateRolePermissions 的 version 一致性校验误报 400。 */
    private int fetchVersion(String token, String roleCode) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/admin/roles/" + roleCode + "/permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.version")).intValue();
    }

    /** ADMIN-011a：R06 管理员成功更新非系统角色（R03）权限，返回 code=0 且权限落库。 */
    @Test
    void admin_updateRolePermissions_success() throws Exception {
        String token = login(ADMIN, PWD);
        int version = fetchVersion(token, "R03");

        String body = "{\"dataScope\":\"ALL_PLANTS\",\"permissions\":[\"exception:VIEW\",\"exception:EDIT\"],\"reason\":\"ADMIN-011回归\",\"version\":" + version + "}";

        mvc.perform(put("/api/v1/admin/roles/R03/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roleCode").value("R03"))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    /** ADMIN-011b：尝试更新 R00 超级管理员权限，返回 code=400 且含「超级管理员」锁定文案（不再 500）。 */
    @Test
    void admin_updateR00_permission_locked() throws Exception {
        String token = login(ADMIN, PWD);
        int version = fetchVersion(token, "R00");

        String body = "{\"dataScope\":\"OWN_PLANT\",\"permissions\":[\"trace:VIEW\"],\"reason\":\"ADMIN-011回归\",\"version\":" + version + "}";

        mvc.perform(put("/api/v1/admin/roles/R00/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("超级管理员")));
    }

    /** ADMIN-011c：畸形 permission（无冒号），返回 code=400 且含「权限格式」文案（参数校验，不再 500）。 */
    @Test
    void admin_updateRolePermissions_badFormat_rejected() throws Exception {
        String token = login(ADMIN, PWD);
        int version = fetchVersion(token, "R03");

        String body = "{\"dataScope\":\"OWN_PLANT\",\"permissions\":[\"bad_format\"],\"reason\":\"ADMIN-011回归\",\"version\":" + version + "}";

        mvc.perform(put("/api/v1/admin/roles/R03/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("权限格式")));
    }

    /** ADMIN-011d：非法 dataScope，返回 code=400 且含「数据范围」文案（参数校验，不再 500）。 */
    @Test
    void admin_updateRolePermissions_invalidDataScope() throws Exception {
        String token = login(ADMIN, PWD);
        int version = fetchVersion(token, "R03");

        String body = "{\"dataScope\":\"XYZ\",\"permissions\":[\"exception:VIEW\"],\"reason\":\"ADMIN-011回归\",\"version\":" + version + "}";

        mvc.perform(put("/api/v1/admin/roles/R03/permissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("数据范围")));
    }
}
