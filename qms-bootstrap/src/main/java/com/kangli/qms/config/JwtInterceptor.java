package com.kangli.qms.config;

import com.kangli.qms.common.AuthConstants;
import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.LoginUserHolder;
import com.kangli.qms.common.ResultCode;
import com.kangli.qms.enums.PlantCode;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.domain.admin.mapper.SysRoleMapper;
import com.kangli.qms.domain.admin.entity.SysRole;
import com.kangli.qms.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT 认证拦截器。
 * <p>
 * 校验流程：
 * <ol>
 *   <li>从 Authorization 头提取 Bearer Token</li>
 *   <li>解析 JWT，校验签名/有效期/类型=ACCESS</li>
 *   <li>校验 Token 是否在黑名单（登出失效）</li>
 *   <li>解析用户身份，注入 ThreadLocal</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    public JwtInterceptor(JwtUtil jwtUtil, RedisTemplate<String, Object> redisTemplate, SysUserMapper userMapper, SysRoleMapper roleMapper) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Browser CORS preflight requests carry no JWT and must reach CorsConfig first.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authHeader = request.getHeader(AUTH_HEADER);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少Authorization头或格式错误");
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Token不能为空");
        }

        // 校验黑名单（登出主动失效）
        Boolean isBlacklisted = redisTemplate.hasKey(AuthConstants.blacklistKey(token));
        if (Boolean.TRUE.equals(isBlacklisted)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Token已失效，请重新登录");
        }

        // 解析 JWT
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (JwtUtil.JwtParseException e) {
            switch (e.getStatus()) {
                case EXPIRED:
                    throw new BusinessException(ResultCode.TOKEN_EXPIRED);
                default:
                    throw new BusinessException(ResultCode.TOKEN_INVALID);
            }
        }

        // 校验 Token 类型为 ACCESS
        String tokenType = claims.get(JwtUtil.CLAIM_TOKEN_TYPE, String.class);
        if (!JwtUtil.TYPE_ACCESS.equals(tokenType)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Token类型错误");
        }

        // 解析用户身份，注入 ThreadLocal
        Long userId = jwtUtil.getUserId(claims);
        String account = claims.get(JwtUtil.CLAIM_ACCOUNT, String.class);
        String roleCode = claims.get(JwtUtil.CLAIM_ROLE_CODE, String.class);
        String plantCodeStr = claims.get(JwtUtil.CLAIM_PLANT_CODE, String.class);
        Boolean canSwitch = claims.get(JwtUtil.CLAIM_CAN_SWITCH, Boolean.class);

        if (userId == null || !StringUtils.hasText(account)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Token载荷缺失用户信息");
        }

        Integer authVersion = claims.get(JwtUtil.CLAIM_AUTH_VERSION, Integer.class);
        SysUser currentUser = userMapper.selectById(userId);
        if (currentUser == null || currentUser.getStatus() == null || currentUser.getStatus() == 0
                || authVersion == null || !authVersion.equals(currentUser.getAuthVersion())) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "会话已失效，请重新登录");
        }

        PlantCode plantCode = PlantCode.of(plantCodeStr);
        if (plantCode == null) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "Token载荷分公司编码无效");
        }

        SysRole role = roleMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode));
        boolean hasAllPlantScope = role != null && "ALL_PLANTS".equals(role.getDataScope());
        PlantCode requestedPlant = PlantCode.of(request.getHeader("X-Plant-Code"));
        if (requestedPlant != null && hasAllPlantScope) {
            plantCode = requestedPlant;
        } else if (requestedPlant != null && requestedPlant != plantCode) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权切换至该分公司");
        }

        LoginUser loginUser = LoginUser.builder()
                .userId(userId)
                .account(account)
                .realName(currentUser != null ? currentUser.getRealName() : null)
                .roleCode(roleCode)
                .plantCode(plantCode)
                .canSwitchArea(hasAllPlantScope)
                .authVersion(authVersion)
                .build();
        LoginUserHolder.set(loginUser);

        log.debug("[JWT认证成功] userId={}, account={}, plantCode={}, roleCode={}",
                userId, account, plantCode, roleCode);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        LoginUserHolder.clear();
    }
}
