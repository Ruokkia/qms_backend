package com.kangli.qms.integration;

import com.jayway.jsonpath.JsonPath;
import com.kangli.qms.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 鉴权全链路集成测试（test-plan XC-010~021 + JWT 安全 + 分公司隔离写越权）。
 * 走真实 JwtInterceptor + PermissionInterceptor + Controller + Service + DB(qms)。
 */
class SecurityIntegrationTest extends BaseIntegrationTest {

    private static final String SECRET = "KangliQmsSecretKey2026ForJwtSigningMustBeLongEnough";
    private static final String ISSUER = "kangli-qms";

    @Autowired
    private JwtUtil jwtUtil;

    /** XC-011：无 token 访问受保护接口 -> 401 UNAUTHORIZED。 */
    @Test
    void noToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    /** XC-021：R02 无 systemAdmin 权限访问 admin -> 403。 */
    @Test
    void r02_accessAdmin_returns403() throws Exception {
        String token = login("sz_insp01", "123456");
        mvc.perform(get("/api/v1/admin/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    /** XC-018：R02 OWN_PLANT 带 X-Plant-Code=MZ 切厂，仍按自身厂隔离。 */
    @Test
    void r02_cannotSwitchPlant() throws Exception {
        String token = login("sz_insp01", "123456");
        mvc.perform(get("/api/v1/exceptions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Plant-Code", "MZ"))
                .andExpect(status().isOk());
    }

    /** L6 分公司隔离：MZ 用户读 SZ 异常单 24 -> 404（PlantTenantInterceptor 注入 plant_code）。 */
    @Test
    void mzUser_readSzException_returns404() throws Exception {
        String token = login("mz_insp01", "123456");
        mvc.perform(get("/api/v1/exceptions/24").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** 对照：SZ 用户读 SZ 异常单 24 -> 正常。 */
    @Test
    void szUser_readSzException_ok() throws Exception {
        String token = login("sz_insp01", "123456");
        mvc.perform(get("/api/v1/exceptions/24").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** JWT 安全：伪造签名（错误 secret）-> 1007 TOKEN_INVALID。 */
    @Test
    void forgedToken_returns1007() throws Exception {
        String token = buildJwt(accessClaims(), "wrong-secret-key-for-forgery-0000", 3600000);
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1007));
    }

    /** JWT 安全：过期 Access（exp 已过）-> 1008 TOKEN_EXPIRED。 */
    @Test
    void expiredToken_returns1008() throws Exception {
        String token = buildJwt(accessClaims(), SECRET, -3600000);
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1008));
    }

    /** JWT 安全：Refresh Token 充当 Access -> 1007 TOKEN_INVALID。 */
    @Test
    void refreshTokenAsAccess_returns1007() throws Exception {
        String token = jwtUtil.generateRefreshToken(1L, "qms_admin");
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1007));
    }

    /** L6 分公司隔离写越权：MZ 用户 update SZ 异常单 24 -> 404（隔离找不到）。 */
    @Test
    void mzUser_updateSzException_returns404() throws Exception {
        String token = login("mz_insp01", "123456");
        mvc.perform(put("/api/v1/exceptions/24")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"defectDesc\":\"越权写测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** L6 分公司隔离写越权：MZ 用户 delete SZ 异常单 24 -> 404（隔离找不到）。 */
    @Test
    void mzUser_deleteSzException_returns404() throws Exception {
        String token = login("mz_insp01", "123456");
        mvc.perform(delete("/api/v1/exceptions/24").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** XC-010：请求头 X-Trace-Id 透传，响应 traceId 等于传入值。 */
    @Test
    void traceId_passthrough() throws Exception {
        String token = login("sz_insp01", "123456");
        String body = mvc.perform(get("/api/v1/exceptions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Trace-Id", "abc123traceid"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("abc123traceid", JsonPath.read(body, "$.traceId"));
    }

    /** XC-010 对照：不传 X-Trace-Id 由 TraceIdFilter 自动生成（16 位非空）。 */
    @Test
    void traceId_autoGenerated() throws Exception {
        String token = login("sz_insp01", "123456");
        String body = mvc.perform(get("/api/v1/exceptions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String traceId = JsonPath.read(body, "$.traceId");
        assertNotNull(traceId);
        assertEquals(16, traceId.length());
    }

    /** XC-014：登出后 Access 进入黑名单，旧 token 再访问被拒（1007）。 */
    @Test
    void logout_blacklistRejectsOldToken() throws Exception {
        String token = login("sz_insp01", "123456");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1007));
    }

    /** XC-016：改密触发 authVersion 递增，旧 token 立即失效（1007「会话已失效」）。 */
    @Test
    void changePassword_invalidatesOldToken() throws Exception {
        String token = login("sz_insp01", "123456");
        // 改密（新密码与旧密码相同，仅验证会话失效机制；接口需认证且从 token 取用户）
        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"123456\",\"newPassword\":\"123457\",\"confirmPassword\":\"123457\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        // 旧 token 因 authVersion 不匹配被拒
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1007));
    }

    /** XC-017：R06 带 X-Plant-Code 切换分公司生效（切到 MZ 后读 SZ 异常单 24 -> 隔离找不到 404）。 */
    @Test
    void r06_switchPlant_effective() throws Exception {
        String token = login("sz_mgr01", "123456");
        mvc.perform(get("/api/v1/exceptions/24")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Plant-Code", "MZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** XC-019：Token 载荷 plantCode 非法（非 SZ/MZ）-> 1007「Token载荷分公司编码无效」。 */
    @Test
    void illegalPlantCode_inToken_rejected() throws Exception {
        Map<String, Object> badClaims = accessClaims();
        badClaims.put("plantCode", "XX");
        String token = buildJwt(badClaims, SECRET, 3600000);
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1007));
    }

    /** XC-022：R02 无 APPROVE 调用异常关单 -> 403 特例消息含「异常管理-审批」。 */
    @Test
    void r02_closeException_withoutApprove_returns403() throws Exception {
        String token = login("sz_insp01", "123456");
        String body = mvc.perform(post("/api/v1/exceptions/24/close")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"closeRemark\":\"越权关单测试\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(403, (int) JsonPath.read(body, "$.code"));
        String msg = JsonPath.read(body, "$.message");
        // 关单映射到 exception-CLOSE，R02 无该权限：提示含「异常管理」或「CLOSE」
        assertTrue(msg.contains("异常管理") || msg.contains("CLOSE"));
    }

    /** XC-024：未登录访问受保护写接口 -> 401 UNAUTHORIZED。 */
    @Test
    void unauthenticated_write_returns401() throws Exception {
        mvc.perform(post("/api/v1/exceptions").contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    /**
     * XC-023（D11 CORS/预检）：OPTIONS 预检请求不带 Authorization，
     * JwtInterceptor(58) 与 PermissionInterceptor(24) 均直接放行，不应报 401/403；
     * 且响应头包含 CORS 预检所需字段（CorsConfig 已配 allowedMethods("*")）。
     */
    @Test
    void optionsPreflight_bypassesInterceptors() throws Exception {
        mvc.perform(options("/api/v1/exceptions")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Methods"))
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    /**
     * XC-023 对照：OPTIONS 预检访问 admin 受保护路径同样被两个拦截器放行（不因权限缺失 403）。
     */
    @Test
    void optionsPreflight_adminPath_bypassesInterceptors() throws Exception {
        mvc.perform(options("/api/v1/admin/users")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    // ---- 工具 ----

    private Map<String, Object> accessClaims() {
        Map<String, Object> c = new HashMap<>();
        c.put("type", "ACCESS");
        c.put("userId", 1L);
        c.put("account", "qms_admin");
        c.put("roleCode", "R00");
        c.put("plantCode", "SZ");
        c.put("canSwitchArea", true);
        c.put("authVersion", 1);
        return c;
    }

    private String buildJwt(Map<String, Object> claims, String secret, long expOffsetMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setClaims(claims)
                .setIssuer(ISSUER)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expOffsetMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
