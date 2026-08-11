package com.kangli.qms.service.auth.impl;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.config.LoginProperties;
import com.kangli.qms.domain.admin.entity.SysRole;
import com.kangli.qms.service.auth.dto.ChangePasswordDTO;
import com.kangli.qms.service.auth.dto.LoginDTO;
import com.kangli.qms.service.auth.dto.RefreshDTO;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysLoginLogMapper;
import com.kangli.qms.domain.admin.mapper.SysRoleMapper;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.domain.admin.mapper.SysRolePermissionMapper;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.util.JwtUtil;
import com.kangli.qms.util.RedisUtil;
import com.kangli.qms.domain.auth.vo.LoginVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl 单测：聚焦登录失败锁定、双 Token 刷新失效、登出双向失效、
 * authVersion 会话失效、密码修改自增 authVersion 等核心安全机制。
 */
class AuthServiceImplTest {

    private SysUserMapper userMapper;
    private SysRoleMapper roleMapper;
    private SysRolePermissionMapper rolePermissionMapper;
    private SysLoginLogMapper loginLogMapper;
    private JwtUtil jwtUtil;
    private RedisUtil redisUtil;
    private LoginProperties loginProps;
    private AuditLogMapper auditLogMapper;
    private AuthServiceImpl service;

    private static final String ACCOUNT = "tester";
    private static final String PW_HASH = new BCryptPasswordEncoder().encode("secret123");

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        rolePermissionMapper = mock(SysRolePermissionMapper.class);
        loginLogMapper = mock(SysLoginLogMapper.class);
        jwtUtil = mock(JwtUtil.class);
        redisUtil = mock(RedisUtil.class);
        loginProps = new LoginProperties();
        loginProps.setMaxFailCount(5);
        loginProps.setFailWindowSeconds(900);
        loginProps.setLockDurationSeconds(1800);
        auditLogMapper = mock(AuditLogMapper.class);

        service = new AuthServiceImpl(userMapper, roleMapper, rolePermissionMapper,
                loginLogMapper, jwtUtil, redisUtil, loginProps, auditLogMapper);
    }

    @AfterEach
    void tearDown() {
        LoginUserHolder.clear();
    }

    private SysUser baseUser() {
        SysUser u = new SysUser();
        u.setId(7L);
        u.setAccount(ACCOUNT);
        u.setPasswordHash(PW_HASH);
        u.setRoleCode("R04");
        u.setPlantCode("SZ");
        u.setPlantName("深圳");
        u.setStatus((short) 1);
        u.setAuthVersion(1);
        return u;
    }

    // ===== AUTH-006 连续失败达到阈值触发锁定 =====

    @Test
    @DisplayName("连续密码错误达到阈值后锁定账号")
    void login_failsReachThreshold_locksAccount() {
        SysUser u = baseUser();
        when(userMapper.selectOne(any())).thenReturn(u);
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        when(redisUtil.incrementWithTtl(anyString(), anyLong())).thenReturn(1L, 2L, 3L, 4L, 5L);
        when(roleMapper.selectOne(any())).thenReturn(new SysRole());

        for (int i = 0; i < 5; i++) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.login(loginDto(ACCOUNT, "wrong"), "127.0.0.1"));
            assertEquals(ResultCode.ACCOUNT_OR_PASSWORD_ERROR.getCode(), ex.getCode());
        }

        // 第5次失败后应设置锁定标记（TTL = 锁定时长）
        verify(redisUtil, times(1)).set(anyString(), eq("1"), eq(1800L));
        // DB 兜底锁定时间
        verify(userMapper, times(1)).updateById(any(SysUser.class));
    }

    // ===== AUTH-004/005 锁定后登录被拒 =====

    @Test
    @DisplayName("账号已锁定时任何登录尝试都被拒绝")
    void login_whenLocked_rejectedWithoutHittingPassword() {
        when(redisUtil.hasKey(anyString())).thenReturn(true);
        when(redisUtil.getExpireSeconds(anyString())).thenReturn(1200L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(loginDto(ACCOUNT, "secret123"), "127.0.0.1"));

        assertEquals(ResultCode.ACCOUNT_LOCKED.getCode(), ex.getCode());
        // 不应查询用户表（Redis 已判定锁定）
        verify(userMapper, never()).selectOne(any());
    }

    // ===== AUTH-005 账号不存在也计入失败 =====

    @Test
    @DisplayName("账号不存在时计入失败计数")
    void login_unknownAccount_recordsFailure() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        when(redisUtil.incrementWithTtl(anyString(), anyLong())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(loginDto("ghost", "x"), "127.0.0.1"));
        assertEquals(ResultCode.ACCOUNT_OR_PASSWORD_ERROR.getCode(), ex.getCode());
        verify(redisUtil, times(1)).incrementWithTtl(anyString(), anyLong());
    }

    // ===== AUTH-011 旧 Refresh Token 刷新后立即失效（滚动刷新） =====

    @Test
    @DisplayName("刷新成功后旧 Refresh Token 立即删除")
    void refresh_rotatesAndInvalidatesOldToken() throws Exception {
        String oldRt = "old-refresh";
        String newAt = "new-access";
        String newRt = "new-refresh";
        SysUser u = baseUser();

        when(redisUtil.get(anyString())).thenReturn(7L);
        when(jwtUtil.parseToken(oldRt)).thenReturn(tokenClaims("REFRESH", 1));
        when(userMapper.selectById(7L)).thenReturn(u);
        when(roleMapper.selectOne(any())).thenReturn(new SysRole());
        when(jwtUtil.generateAccessToken(anyLong(), anyString(), anyString(), any(PlantCode.class), anyBoolean(), anyInt()))
                .thenReturn(newAt);
        when(jwtUtil.generateRefreshToken(anyLong(), anyString(), anyInt())).thenReturn(newRt);

        LoginVO vo = service.refresh(refreshDto(oldRt));

        // 旧 token 删除 + 新 token 写入
        verify(redisUtil, times(1)).delete(eq("qms:auth:refresh:" + oldRt));
        verify(redisUtil, times(1)).set(eq("qms:auth:refresh:" + newRt), eq(7L), anyLong());
        assertEquals(newAt, vo.getToken());
        assertEquals(newRt, vo.getRefreshToken());
    }

    // ===== AUTH-015 authVersion 变更后旧 Refresh 失效 =====

    @Test
    @DisplayName("密码修改导致 authVersion 变更后旧 Refresh 刷新被拒")
    void refresh_authVersionMismatch_rejected() throws Exception {
        String rt = "refresh-v1";
        SysUser u = baseUser();
        u.setAuthVersion(2); // 用户已改密，token 仍是 v1

        when(redisUtil.get(anyString())).thenReturn(7L);
        when(jwtUtil.parseToken(rt)).thenReturn(tokenClaims("REFRESH", 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.refresh(refreshDto(rt)));
        assertEquals(ResultCode.REFRESH_TOKEN_INVALID.getCode(), ex.getCode());
    }

    // ===== AUTH-013 登出双失效 =====

    @Test
    @DisplayName("登出时 Refresh Token 被删除且 Access 进入黑名单")
    void logout_invalidatesBothTokens() throws Exception {
        String at = "access-token";
        String rt = "refresh-token";
        io.jsonwebtoken.Claims claims = tokenClaims("ACCESS", 1);
        when(jwtUtil.parseToken(at)).thenReturn(claims);
        when(redisUtil.delete(anyString())).thenReturn(true);

        service.logout(at, rt);

        verify(redisUtil, times(1)).delete(eq("qms:auth:refresh:" + rt));
        verify(redisUtil, times(1)).set(eq("qms:auth:blacklist:" + at), eq("1"), anyLong());
    }

    // ===== AUTH-014 密码修改自增 authVersion =====

    @Test
    @DisplayName("自主修改密码后 authVersion 自增，旧会话失效")
    void changePassword_incrementsAuthVersion() {
        SysUser u = baseUser();
        LoginUserHolder.set(LoginUser.builder().userId(7L).account(ACCOUNT).build());

        when(userMapper.selectById(7L)).thenReturn(u);
        when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setCurrentPassword("secret123");
        dto.setNewPassword("newSecret456");
        dto.setConfirmPassword("newSecret456");

        service.changePassword(dto);

        assertEquals(2, u.getAuthVersion());
        verify(userMapper, times(1)).updateById(u);
        verify(auditLogMapper, times(1)).insert(any());
    }

    // ===== AUTH 密码修改-当前密码错误 =====

    @Test
    @DisplayName("修改密码时当前密码错误被拒")
    void changePassword_wrongCurrentPassword_rejected() {
        SysUser u = baseUser();
        LoginUserHolder.set(LoginUser.builder().userId(7L).account(ACCOUNT).build());
        when(userMapper.selectById(7L)).thenReturn(u);

        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setCurrentPassword("bad");
        dto.setNewPassword("newSecret456");
        dto.setConfirmPassword("newSecret456");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.changePassword(dto));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(auditLogMapper, never()).insert(any());
    }

    private LoginDTO loginDto(String account, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setAccount(account);
        dto.setPassword(password);
        return dto;
    }

    private RefreshDTO refreshDto(String refreshToken) {
        RefreshDTO dto = new RefreshDTO();
        dto.setRefreshToken(refreshToken);
        return dto;
    }

    private io.jsonwebtoken.Claims tokenClaims(String type, int authVersion) {
        return new io.jsonwebtoken.impl.DefaultClaims() {{
            put(com.kangli.qms.util.JwtUtil.CLAIM_TOKEN_TYPE, type);
            put(com.kangli.qms.util.JwtUtil.CLAIM_AUTH_VERSION, authVersion);
            put("userId", 7L);
            put("account", ACCOUNT);
            put("exp", java.time.Instant.now().plusSeconds(3600).getEpochSecond());
        }};
    }
}
