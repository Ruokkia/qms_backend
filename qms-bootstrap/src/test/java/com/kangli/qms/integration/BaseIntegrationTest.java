package com.kangli.qms.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.kangli.qms.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * 集成测试基类：@SpringBootTest 启动完整上下文 + MockMvc 走拦截器链。
 * 注：qms_test 独立库因开发者迁移顺序问题（V20260806151000001 引用后续才加的列）
 * 无法从零迁移，故临时连已迁移的开发库 qms；@Transactional 回滚 DB 改动，不污染开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected RedisUtil redisUtil;
    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    /** 真实登录拿 token（走完整鉴权链），登录写 Redis refresh + DB loginLog，@Transactional 回滚 DB。 */
    protected String login(String account, String pwd) throws Exception {
        String body = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account + "\",\"password\":\"" + pwd + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    /** 登录并返回用户 id（从 login 响应 userInfo.userId）。 */
    protected Long loginUserId(String account, String pwd) throws Exception {
        String body = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account + "\",\"password\":\"" + pwd + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.userInfo.userId")).longValue();
    }

    @AfterEach
    void clearRedis() {
        // 清测试期间产生的认证 key（失败计数/锁定/refresh/黑名单），避免干扰后续测试
        try {
            redisUtil.delete("qms:auth:fail:qms_admin");
            redisUtil.delete("qms:auth:fail:sz_insp01");
            redisUtil.delete("qms:auth:fail:sz_mgr01");
            redisUtil.delete("qms:auth:fail:mz_insp01");
            redisUtil.delete("qms:auth:lock:qms_admin");
            redisUtil.delete("qms:auth:lock:sz_insp01");
            redisUtil.delete("qms:auth:lock:sz_mgr01");
            redisUtil.delete("qms:auth:lock:mz_insp01");
            // 按前缀清理动态 token 相关 key（黑名单/refresh），避免跨测试串扰
            for (String pattern : new String[]{"qms:auth:blacklist:*", "qms:auth:refresh:*", "qms:auth:blacklist*", "qms:auth:refresh*"}) {
                stringRedisTemplate.delete(stringRedisTemplate.keys(pattern));
            }
        } catch (Exception ignored) {
            // 清理失败不阻断测试
        }
    }
}
