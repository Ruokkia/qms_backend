package com.kangli.qms.config;

import com.kangli.qms.common.AuthConstants;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * STOMP CONNECT 帧鉴权拦截器（D10：实时通知）。
 * <p>token 不再通过 URL query 传递（避免泄露到访问日志/浏览器历史/Referer），
 * 改由客户端在 STOMP CONNECT 帧的 {@code Authorization: Bearer} 头中携带，
 * 经已建立的 WebSocket 数据通道传输，不落入任何 URL。</p>
 * <p>校验流程与 HTTP 层 {@link JwtInterceptor} 保持一致：
 * <ol>
 *   <li>解析 JWT（签名/有效期）</li>
 *   <li>类型必须为 ACCESS</li>
 *   <li>登出黑名单比对</li>
 *   <li><b>authVersion 与用户状态校验</b>——改密/禁用后旧 token 建立的 WS 连接将被拒绝</li>
 * </ol>
 * 校验通过后将 {@link StompPrincipal}（name = userId）绑定到会话，
 * 使 {@code /user/{userId}/queue/notifications} 用户目的地正确路由。</p>
 * <p>校验失败抛出 {@link MessageDeliveryException}，Spring 会向客户端回送 ERROR 帧并断开连接；
 * 错误消息以 {@link #AUTH_FAIL_PREFIX} 开头，前端据此识别"鉴权失败"并停止自动重连。</p>
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** 鉴权失败错误前缀：前端 onStompError 依据它区分"鉴权失败（不重连）"与"网络异常（可重连）" */
    public static final String AUTH_FAIL_PREFIX = "WS-AUTH-FAILED";

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SysUserMapper userMapper;

    public StompAuthChannelInterceptor(JwtUtil jwtUtil, RedisTemplate<String, Object> redisTemplate,
                                       SysUserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticate(message, accessor);
        } else if ((StompCommand.SUBSCRIBE.equals(command) || StompCommand.SEND.equals(command))
                && accessor.getUser() == null) {
            // 防御性校验：未通过 CONNECT 鉴权的会话不允许订阅/发送
            throw new MessageDeliveryException(message, AUTH_FAIL_PREFIX + ": 会话未认证");
        }
        return message;
    }

    private void authenticate(Message<?> message, StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (!StringUtils.hasText(token)) {
            log.warn("[WS-CONNECT] 缺少 Authorization 头，拒绝连接");
            throw new MessageDeliveryException(message, AUTH_FAIL_PREFIX + ": 缺少凭证");
        }

        // 1. 解析 JWT（签名/有效期）
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            log.warn("[WS-CONNECT] token 解析失败：{}", e.getMessage());
            throw new MessageDeliveryException(message, AUTH_FAIL_PREFIX + ": 凭证无效或已过期");
        }

        // 2. 类型必须为 ACCESS
        String tokenType = claims.get(JwtUtil.CLAIM_TOKEN_TYPE, String.class);
        if (!JwtUtil.TYPE_ACCESS.equals(tokenType)) {
            log.warn("[WS-CONNECT] token 类型非 ACCESS，拒绝连接");
            throw new MessageDeliveryException(message, AUTH_FAIL_PREFIX + ": 凭证类型错误");
        }

        // 3. 登出黑名单比对（与 JwtInterceptor 一致的主动失效策略）
        Boolean blacklisted = redisTemplate.hasKey(AuthConstants.blacklistKey(token));
        if (Boolean.TRUE.equals(blacklisted)) {
            log.warn("[WS-CONNECT] token 已失效（黑名单），拒绝连接");
            throw new MessageDeliveryException(message, AUTH_FAIL_PREFIX + ": 凭证已失效");
        }

        Long userId = jwtUtil.getUserId(claims);
        if (userId == null) {
            log.warn("[WS-CONNECT] 无法从 token 解析 userId，拒绝连接");
            throw new MessageDeliveryException(message, AUTH_FAIL_PREFIX + ": 凭证载荷缺失用户信息");
        }

        // 4. authVersion + 用户状态校验：改密/权限变更/禁用后，旧 token 无法建立新 WS 连接
        Integer authVersion = claims.get(JwtUtil.CLAIM_AUTH_VERSION, Integer.class);
        SysUser currentUser = userMapper.selectById(userId);
        if (currentUser == null || currentUser.getStatus() == null || currentUser.getStatus() == 0
                || authVersion == null || !authVersion.equals(currentUser.getAuthVersion())) {
            log.warn("[WS-CONNECT] authVersion/用户状态校验失败，userId={}，拒绝连接", userId);
            throw new MessageDeliveryException(message, AUTH_FAIL_PREFIX + ": 会话已失效，请重新登录");
        }

        accessor.setUser(new StompPrincipal(String.valueOf(userId)));
        log.debug("[WS-CONNECT] 鉴权成功，userId={}", userId);
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String auth = accessor.getFirstNativeHeader(AUTH_HEADER);
        if (StringUtils.hasText(auth) && auth.startsWith(BEARER_PREFIX)) {
            return auth.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
