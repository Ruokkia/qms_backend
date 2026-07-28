package com.kangli.qms.util;

import com.kangli.qms.config.JwtProperties;
import com.kangli.qms.enums.PlantCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类（双 Token 机制）。
 * <p>
 * Access Token：有效期 2 小时，payload 含 userId/account/roleCode/plantCode/canSwitchArea/type=ACCESS。
 * Refresh Token：有效期 7 天，payload 含 userId/account/type=REFRESH（不携带敏感权限信息）。
 * </p>
 */
@Slf4j
@Component
public class JwtUtil {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** Token 类型 claim key */
    public static final String CLAIM_TOKEN_TYPE = "type";
    /** Access Token 类型值 */
    public static final String TYPE_ACCESS = "ACCESS";
    /** Refresh Token 类型值 */
    public static final String TYPE_REFRESH = "REFRESH";

    // ---- JWT Payload 字段 key ----
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_ACCOUNT = "account";
    public static final String CLAIM_ROLE_CODE = "roleCode";
    public static final String CLAIM_PLANT_CODE = "plantCode";
    public static final String CLAIM_CAN_SWITCH = "canSwitchArea";
    public static final String CLAIM_AUTH_VERSION = "authVersion";

    /**
     * 生成 Access Token（有效期 2 小时）。
     *
     * @param userId        用户ID
     * @param account       登录账号
     * @param roleCode      角色编码
     * @param plantCode     分公司编码
     * @param canSwitchArea 是否可切换分公司（仅 R06）
     * @return JWT Access Token
     */
    public String generateAccessToken(Long userId, String account, String roleCode,
                                      PlantCode plantCode, boolean canSwitchArea) {
        return generateAccessToken(userId, account, roleCode, plantCode, canSwitchArea, 1);
    }

    public String generateAccessToken(Long userId, String account, String roleCode,
                                      PlantCode plantCode, boolean canSwitchArea, int authVersion) {
        Map<String, Object> claims = new HashMap<>(8);
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_ACCOUNT, account);
        claims.put(CLAIM_ROLE_CODE, roleCode);
        claims.put(CLAIM_PLANT_CODE, plantCode.name());
        claims.put(CLAIM_CAN_SWITCH, canSwitchArea);
        claims.put(CLAIM_AUTH_VERSION, authVersion);
        claims.put(CLAIM_TOKEN_TYPE, TYPE_ACCESS);
        return buildToken(claims, properties.getAccessTokenExpiration());
    }

    /**
     * 生成 Refresh Token（有效期 7 天，仅含身份标识，不含权限信息）。
     *
     * @param userId  用户ID
     * @param account 登录账号
     * @return JWT Refresh Token
     */
    public String generateRefreshToken(Long userId, String account) {
        return generateRefreshToken(userId, account, 1);
    }

    public String generateRefreshToken(Long userId, String account, int authVersion) {
        Map<String, Object> claims = new HashMap<>(4);
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_ACCOUNT, account);
        claims.put(CLAIM_TOKEN_TYPE, TYPE_REFRESH);
        claims.put(CLAIM_AUTH_VERSION, authVersion);
        return buildToken(claims, properties.getRefreshTokenExpiration());
    }

    /**
     * 构建 JWT Token（通用内部方法）。
     */
    private String buildToken(Map<String, Object> claims, long expirationSeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000L);
        JwtBuilder builder = Jwts.builder()
                .setClaims(claims)
                .setIssuer(properties.getIssuer())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(signingKey, SignatureAlgorithm.HS256);
        return builder.compact();
    }

    /**
     * 解析 Token 的 Claims。
     *
     * @return Claims；若 Token 无效/过期/签名错误则抛出 {@link JwtParseException}
     */
    public Claims parseToken(String token) throws JwtParseException {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new JwtParseException(JwtParseStatus.EXPIRED, e.getMessage());
        } catch (UnsupportedJwtException | MalformedJwtException e) {
            throw new JwtParseException(JwtParseStatus.MALFORMED, e.getMessage());
        } catch (SignatureException e) {
            throw new JwtParseException(JwtParseStatus.SIGNATURE_INVALID, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new JwtParseException(JwtParseStatus.EMPTY, e.getMessage());
        }
    }

    /**
     * 从 Token 中提取用户ID。
     */
    public Long getUserId(Claims claims) {
        Object id = claims.get(CLAIM_USER_ID);
        if (id instanceof Integer) {
            return ((Integer) id).longValue();
        }
        if (id instanceof Long) {
            return (Long) id;
        }
        return null;
    }

    /**
     * 获取 Access Token 有效期（秒）。
     */
    public long getAccessTokenExpiration() {
        return properties.getAccessTokenExpiration();
    }

    /**
     * 获取 Refresh Token 有效期（秒）。
     */
    public long getRefreshTokenExpiration() {
        return properties.getRefreshTokenExpiration();
    }

    /** JWT 解析状态 */
    public enum JwtParseStatus {
        EXPIRED, MALFORMED, SIGNATURE_INVALID, EMPTY
    }

    /** JWT 解析异常 */
    public static class JwtParseException extends Exception {
        private final JwtParseStatus status;

        public JwtParseException(JwtParseStatus status, String message) {
            super(message);
            this.status = status;
        }

        public JwtParseStatus getStatus() {
            return status;
        }
    }
}
