package com.kangli.qms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性。
 * <p>对应 application.yml 中 jwt.* 配置项。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥（HS256，至少 32 字节） */
    private String secret;

    /** Access Token 有效期（秒），默认 7200=2小时 */
    private long accessTokenExpiration = 7200;

    /** Refresh Token 有效期（秒），默认 604800=7天 */
    private long refreshTokenExpiration = 604800;

    /** Token 签发者 */
    private String issuer = "kangli-qms";
}
