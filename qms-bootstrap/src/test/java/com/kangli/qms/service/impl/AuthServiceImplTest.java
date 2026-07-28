package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.kangli.qms.config.LoginProperties;
import com.kangli.qms.dto.LoginDTO;
import com.kangli.qms.entity.SysRole;
import com.kangli.qms.entity.SysUser;
import com.kangli.qms.mapper.SysLoginLogMapper;
import com.kangli.qms.mapper.SysRoleMapper;
import com.kangli.qms.mapper.SysRolePermissionMapper;
import com.kangli.qms.mapper.SysUserMapper;
import com.kangli.qms.util.JwtUtil;
import com.kangli.qms.util.RedisUtil;
import com.kangli.qms.vo.LoginVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    @Test
    void login_shouldExposeAreaSwitchPermissionForAllPlantsRole() {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRolePermissionMapper permissionMapper = mock(SysRolePermissionMapper.class);
        SysLoginLogMapper loginLogMapper = mock(SysLoginLogMapper.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        LoginProperties loginProperties = new LoginProperties();

        AuthServiceImpl service = new AuthServiceImpl(userMapper, roleMapper, permissionMapper,
                loginLogMapper, jwtUtil, redisUtil, loginProperties);

        SysUser user = new SysUser();
        user.setId(1L);
        user.setAccount("sz_op01");
        user.setRealName("测试操作员");
        user.setRoleCode("R01");
        user.setPlantCode("SZ");
        user.setPlantName("深圳");
        user.setStatus((short) 1);
        user.setAuthVersion(1);
        user.setPasswordHash(new BCryptPasswordEncoder().encode("123456"));

        SysRole role = new SysRole();
        role.setRoleCode("R01");
        role.setRoleName("操作员");
        role.setDataScope("ALL_PLANTS");

        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(role);
        when(jwtUtil.generateAccessToken(anyLong(), anyString(), anyString(), any(), anyBoolean(), anyInt()))
                .thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyLong(), anyString(), anyInt())).thenReturn("refresh-token");
        when(jwtUtil.getRefreshTokenExpiration()).thenReturn(3600L);
        when(jwtUtil.getAccessTokenExpiration()).thenReturn(3600L);

        LoginDTO request = new LoginDTO();
        request.setAccount("sz_op01");
        request.setPassword("123456");

        LoginVO response = service.login(request, "127.0.0.1");

        assertTrue(response.getUserInfo().isCanSwitchArea());
    }
}
