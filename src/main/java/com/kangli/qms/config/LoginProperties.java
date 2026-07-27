package com.kangli.qms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录安全策略配置。
 * <p>对应 application.yml 中 login.* 配置项。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "login")
public class LoginProperties {

    /** 最大连续失败次数，默认 5 次 */
    private int maxFailCount = 5;

    /** 失败计数窗口（秒），默认 900=15分钟 */
    private long failWindowSeconds = 900;

    /** 锁定时长（秒），默认 1800=30分钟 */
    private long lockDurationSeconds = 1800;

    /** 验证码 TTL（秒），默认 300=5分钟 */
    private long captchaTtlSeconds = 300;
}
