package com.kangli.qms.config;

import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.domain.admin.mapper.SysRoleMapper;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.util.JwtUtil;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtInterceptorTest {

    @AfterEach
    void clearLoginUser() {
        LoginUserHolder.clear();
    }

    @Test
    void preHandle_populatesRealNameFromCurrentUser() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        JwtInterceptor interceptor = new JwtInterceptor(jwtUtil, redisTemplate, userMapper, roleMapper);

        DefaultClaims claims = new DefaultClaims();
        claims.put(JwtUtil.CLAIM_TOKEN_TYPE, JwtUtil.TYPE_ACCESS);
        claims.put(JwtUtil.CLAIM_USER_ID, 7L);
        claims.put(JwtUtil.CLAIM_ACCOUNT, "sz_qe01");
        claims.put(JwtUtil.CLAIM_ROLE_CODE, "R04");
        claims.put(JwtUtil.CLAIM_PLANT_CODE, "SZ");
        claims.put(JwtUtil.CLAIM_AUTH_VERSION, 1);
        SysUser user = new SysUser();
        user.setStatus((short) 1);
        user.setAuthVersion(1);
        user.setRealName("赵六");

        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(jwtUtil.parseToken("token")).thenReturn(claims);
        when(jwtUtil.getUserId(claims)).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertEquals("赵六", LoginUserHolder.get().getRealName());
    }
}
