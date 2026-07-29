package com.kangli.qms.service.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.common.AuthConstants;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.config.LoginProperties;
import com.kangli.qms.service.auth.dto.LoginDTO;
import com.kangli.qms.service.auth.dto.RefreshDTO;
import com.kangli.qms.service.auth.dto.ChangePasswordDTO;
import com.kangli.qms.domain.auth.entity.SysLoginLog;
import com.kangli.qms.domain.admin.entity.AuditLog;
import com.kangli.qms.domain.admin.entity.SysRole;
import com.kangli.qms.domain.admin.entity.SysRolePermission;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.domain.auth.mapper.SysLoginLogMapper;
import com.kangli.qms.domain.admin.mapper.SysRoleMapper;
import com.kangli.qms.domain.admin.mapper.SysRolePermissionMapper;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.admin.mapper.AuditLogMapper;
import com.kangli.qms.service.auth.AuthService;
import com.kangli.qms.util.JwtUtil;
import com.kangli.qms.util.RedisUtil;
import com.kangli.qms.domain.auth.vo.LoginVO;
import com.kangli.qms.domain.auth.vo.UserInfoVO;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证服务实现。
 * <p>
 * 核心机制：
 * <ul>
 *   <li>双 Token：Access 2h + Refresh 7d</li>
 *   <li>Redis 四项：验证码 / Refresh Token / 失败计数 / 锁定标记 / 黑名单</li>
 *   <li>BCrypt 密码校验</li>
 *   <li>图形验证码校验（登录前必校，防暴力枚举）</li>
 *   <li>分公司数据隔离</li>
 *   <li>登出双向失效（Access 黑名单 + Refresh 删除）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 日期时间格式（与前端对齐） */
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> SUPER_ADMIN_MODULES = List.of(
            "systemAdmin", "trace", "incoming", "exception", "fai", "spc",
            "productionDefect", "processTools", "finishedGoods");

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final AuditLogMapper auditLogMapper;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final LoginProperties loginProps;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthServiceImpl(SysUserMapper userMapper, SysRoleMapper roleMapper, SysRolePermissionMapper rolePermissionMapper,
                           SysLoginLogMapper loginLogMapper, JwtUtil jwtUtil,
                           RedisUtil redisUtil, LoginProperties loginProps, AuditLogMapper auditLogMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.loginLogMapper = loginLogMapper;
        this.auditLogMapper = auditLogMapper;
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
        this.loginProps = loginProps;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public LoginVO login(LoginDTO dto, String loginIp) {
        String account = dto.getAccount().trim();

        // 1. 检查账号是否被锁定（Redis 优先，DB 兜底）
        checkAccountLocked(account);

        // 2. 查询用户
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getAccount, account));
        PlantCode userPlant = user != null ? PlantCode.of(user.getPlantCode()) : null;

        if (user == null) {
            recordLoginFailure(account, null, null, loginIp, "账号不存在");
            throw new BusinessException(ResultCode.ACCOUNT_OR_PASSWORD_ERROR);
        }

        // 3. 校验账号状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            recordLoginFailure(account, user.getId(), userPlant, loginIp, "账号已禁用");
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        // 4. 校验密码（BCrypt）
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            recordLoginFailure(account, user.getId(), userPlant, loginIp, "密码错误");
            throw new BusinessException(ResultCode.ACCOUNT_OR_PASSWORD_ERROR);
        }

        // 5. 登录成功：清除失败计数
        redisUtil.delete(AuthConstants.failCountKey(account));
        redisUtil.delete(AuthConstants.lockKey(account));

        // 6. 查询角色名称
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, user.getRoleCode()));
        String roleName = (role != null) ? role.getRoleName() : "";

        // 7. 判断是否可切换分公司（根据角色 data_scope 判断，ALL_PLANTS 即可切换）
        boolean canSwitch = role != null && "ALL_PLANTS".equals(role.getDataScope());

        // 8. 生成双 Token
        if (userPlant == null) {
            log.error("[登录失败] 用户分公司信息缺失 account={}", account);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "用户分公司信息缺失，请联系管理员");
        }
        int authVersion = user.getAuthVersion() == null ? 1 : user.getAuthVersion();
        String accessToken = jwtUtil.generateAccessToken(user.getId(), account,
                user.getRoleCode(), userPlant, canSwitch, authVersion);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), account, authVersion);

        // 12. Refresh Token 写入 Redis（设 TTL）
        redisUtil.set(AuthConstants.refreshKey(refreshToken), user.getId(),
                jwtUtil.getRefreshTokenExpiration());

        // 13. 更新最后登录时间
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        update.setLoginFailCount(0);
        update.setLockedUntil(null);
        userMapper.updateById(update);

        // 14. 写入登录日志
        writeLoginLog(user.getId(), account, userPlant, loginIp, "成功", null);

        log.info("[登录成功] userId={}, account={}, plantCode={}, roleCode={}",
                user.getId(), account, userPlant, user.getRoleCode());

        // 15. 组装响应
        return buildLoginVO(accessToken, refreshToken, user, roleName, canSwitch);
    }

    @Override
    public LoginVO refresh(RefreshDTO dto) {
        String refreshToken = dto.getRefreshToken().trim();

        // 1. 从 Redis 校验 Refresh Token 是否存在
        Object userIdObj = redisUtil.get(AuthConstants.refreshKey(refreshToken));
        if (userIdObj == null) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        Long userId = toLong(userIdObj);

        // 2. 解析 Refresh Token 的 JWT（双重校验）
        Claims claims;
        try {
            claims = jwtUtil.parseToken(refreshToken);
        } catch (JwtUtil.JwtParseException e) {
            // Redis 存在但 JWT 过期，也视为无效
            redisUtil.delete(AuthConstants.refreshKey(refreshToken));
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        // 校验类型为 REFRESH
        String tokenType = claims.get(JwtUtil.CLAIM_TOKEN_TYPE, String.class);
        if (!JwtUtil.TYPE_REFRESH.equals(tokenType)) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        // 3. 旧 Refresh Token 立即失效（滚动刷新）
        redisUtil.delete(AuthConstants.refreshKey(refreshToken));

        // 4. 查询用户信息
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID, "用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        Integer tokenAuthVersion = claims.get(JwtUtil.CLAIM_AUTH_VERSION, Integer.class);
        if (tokenAuthVersion == null || !tokenAuthVersion.equals(user.getAuthVersion())) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID, "会话已失效，请重新登录");
        }

        // 5. 查询角色
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, user.getRoleCode()));
        String roleName = (role != null) ? role.getRoleName() : "";
        boolean canSwitch = role != null && "ALL_PLANTS".equals(role.getDataScope());

        // 6. 生成新的双 Token
        PlantCode userPlant = PlantCode.of(user.getPlantCode());
        int authVersion = user.getAuthVersion() == null ? 1 : user.getAuthVersion();
        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getAccount(),
                user.getRoleCode(), userPlant, canSwitch, authVersion);
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getAccount(), authVersion);

        // 7. 新 Refresh Token 写入 Redis
        redisUtil.set(AuthConstants.refreshKey(newRefreshToken), user.getId(),
                jwtUtil.getRefreshTokenExpiration());

        log.info("[Token刷新成功] userId={}, account={}", user.getId(), user.getAccount());

        return buildLoginVO(newAccessToken, newRefreshToken, user, roleName, canSwitch);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        // 1. Access Token 加入黑名单（TTL = 剩余有效期）
        if (StringUtils.hasText(accessToken)) {
            try {
                Claims claims = jwtUtil.parseToken(accessToken);
                long expMs = claims.getExpiration().getTime();
                long nowMs = System.currentTimeMillis();
                long remainingSeconds = (expMs - nowMs) / 1000;
                if (remainingSeconds > 0) {
                    redisUtil.set(AuthConstants.blacklistKey(accessToken), "1",
                            remainingSeconds);
                }
            } catch (JwtUtil.JwtParseException e) {
                // Token 已过期或无效，无需加入黑名单
                log.debug("[登出] Access Token 已过期或无效，跳过黑名单");
            }
        }

        // 2. 删除 Redis 中的 Refresh Token（立即失效）
        if (StringUtils.hasText(refreshToken)) {
            Boolean deleted = redisUtil.delete(AuthConstants.refreshKey(refreshToken));
            log.debug("[登出] Refresh Token 删除结果={}", deleted);
        }

        // 3. 记录登出日志
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser != null) {
            log.info("[登出成功] userId={}, account={}", loginUser.getUserId(), loginUser.getAccount());
        }
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordDTO dto) {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null) throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前密码不正确");
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "两次输入的新密码不一致");
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setAuthVersion((user.getAuthVersion() == null ? 1 : user.getAuthVersion()) + 1);
        userMapper.updateById(user);
        AuditLog auditLog = new AuditLog();
        auditLog.setTableName("sys_user"); auditLog.setRecordId(user.getId()); auditLog.setOperationType("CHANGE_PASSWORD");
        auditLog.setAfterData("用户自主修改密码"); auditLog.setOperatorId(user.getId()); auditLog.setOperatorName(user.getAccount());
        auditLog.setPlantCode(user.getPlantCode()); auditLogMapper.insert(auditLog);
        log.info("[密码已修改] userId={}, account={}", user.getId(), user.getAccount());
    }

    @Override
    public UserInfoVO getCurrentUser() {
        LoginUser loginUser = LoginUserHolder.get();
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, user.getRoleCode()));
        String roleName = (role != null) ? role.getRoleName() : "";

        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getId());
        vo.setAccount(user.getAccount());
        vo.setRealName(user.getRealName());
        vo.setRoleCode(user.getRoleCode());
        vo.setRoleName(roleName);
        vo.setPlantCode(user.getPlantCode());
        vo.setPlantName(user.getPlantName());
        vo.setCanSwitchArea(role != null && "ALL_PLANTS".equals(role.getDataScope()));
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt() != null
                ? user.getLastLoginAt().format(DT_FMT) : null);
        vo.setModulePermissions(modulePermissions(user.getRoleCode()));
        return vo;
    }

    // ============ 内部方法 ============

    /**
     * 检查账号是否被锁定。
     */
    private void checkAccountLocked(String account) {
        // Redis 优先
        Boolean locked = redisUtil.hasKey(AuthConstants.lockKey(account));
        if (Boolean.TRUE.equals(locked)) {
            long ttl = getTtlSeconds(AuthConstants.lockKey(account));
            long minutes = (ttl + 59) / 60;
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED,
                    "账号已锁定，请" + minutes + "分钟后重试");
        }
    }

    /**
     * 记录登录失败：递增失败计数 → 达到阈值则锁定。
     */
    private void recordLoginFailure(String account, Long userId, PlantCode plantCode,
                                     String loginIp, String failReason) {
        // 1. 递增失败计数（首次设置 TTL = 失败窗口）
        long failCount = redisUtil.incrementWithTtl(
                AuthConstants.failCountKey(account), loginProps.getFailWindowSeconds());

        log.warn("[登录失败] account={}, failCount={}, reason={}", account, failCount, failReason);

        // 2. 写入失败日志
        writeLoginLog(userId, account, plantCode, loginIp, "失败", failReason);

        // 3. 达到阈值 → 写入锁定标记
        if (failCount >= loginProps.getMaxFailCount()) {
            redisUtil.set(AuthConstants.lockKey(account), "1",
                    loginProps.getLockDurationSeconds());
            // 更新 DB 锁定时间（兜底）
            if (userId != null) {
                SysUser update = new SysUser();
                update.setId(userId);
                update.setLockedUntil(LocalDateTime.now().plusSeconds(loginProps.getLockDurationSeconds()));
                update.setLoginFailCount((int) failCount);
                userMapper.updateById(update);
            }
            // 追加一条锁定日志
            writeLoginLog(userId, account, plantCode, loginIp, "锁定",
                    "连续失败" + failCount + "次，锁定" + (loginProps.getLockDurationSeconds() / 60) + "分钟");
        }
    }

    /**
     * 写入登录日志。
     */
    private void writeLoginLog(Long userId, String account, PlantCode plantCode,
                              String loginIp, String status, String failReason) {
        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setUserId(userId);
        loginLog.setAccount(account);
        loginLog.setPlantCode(plantCode != null ? plantCode.name() : null);
        loginLog.setPlantName(plantCode != null ? plantCode.getChineseName() : null);
        loginLog.setLoginIp(loginIp);
        loginLog.setLoginStatus(status);
        loginLog.setFailReason(failReason);
        loginLog.setLoginTime(LocalDateTime.now());
        loginLog.setCreatedBy(account);
        loginLogMapper.insert(loginLog);
    }

    /**
     * 组装登录响应 VO。
     */
    private LoginVO buildLoginVO(String accessToken, String refreshToken,
                                 SysUser user, String roleName, boolean canSwitchArea) {
        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setUserId(user.getId());
        userInfo.setAccount(user.getAccount());
        userInfo.setRealName(user.getRealName());
        userInfo.setRoleCode(user.getRoleCode());
        userInfo.setRoleName(roleName);
        userInfo.setPlantCode(user.getPlantCode());
        userInfo.setPlantName(user.getPlantName());
        userInfo.setCanSwitchArea(canSwitchArea);
        userInfo.setStatus(user.getStatus());
        userInfo.setModulePermissions(modulePermissions(user.getRoleCode()));

        LoginVO vo = new LoginVO();
        vo.setToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setTokenExpireIn(jwtUtil.getAccessTokenExpiration());
        vo.setUserInfo(userInfo);
        return vo;
    }

    /**
     * Object 转 Long。
     */
    private Long toLong(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return Long.parseLong(obj.toString());
    }

    private List<String> modulePermissions(String roleCode) {
        if ("R00".equals(roleCode)) {
            return SUPER_ADMIN_MODULES;
        }
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleCode, roleCode))
                .stream()
                .map(SysRolePermission::getModuleCode)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取 Key 的真实剩余 TTL（秒）。
     */
    private long getTtlSeconds(String key) {
        return redisUtil.getExpireSeconds(key);
    }
}
