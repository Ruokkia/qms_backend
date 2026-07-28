package com.kangli.qms.common;

/**
 * 认证模块 Redis Key 常量。
 * <p>所有 Key 统一前缀 qms:auth:，按用途分类。</p>
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    /** Key 前缀 */
    public static final String PREFIX = "qms:auth:";

    // ---- Refresh Token ----
    /** Refresh Token 存储：key = qms:auth:refresh:{refreshToken}，value = userId */
    public static final String REFRESH_TOKEN_PREFIX = PREFIX + "refresh:";

    // ---- 登录失败锁定 ----
    /** 失败计数：key = qms:auth:fail:{account}，value = 失败次数 */
    public static final String FAIL_COUNT_PREFIX = PREFIX + "fail:";
    /** 锁定标记：key = qms:auth:lock:{account}，value = "1"，带 TTL 自动解锁 */
    public static final String LOCK_PREFIX = PREFIX + "lock:";

    // ---- 验证码 ----
    /** 验证码：key = qms:auth:captcha:{captchaKey}，value = 验证码文本 */
    public static final String CAPTCHA_PREFIX = PREFIX + "captcha:";

    // ---- Access Token 黑名单（登出主动失效）----
    /** Token 黑名单：key = qms:auth:blacklist:{token}，value = "1"，TTL = token 剩余有效期 */
    public static final String BLACKLIST_PREFIX = PREFIX + "blacklist:";

    /**
     * 构建 Refresh Token 的 Redis Key。
     */
    public static String refreshKey(String refreshToken) {
        return REFRESH_TOKEN_PREFIX + refreshToken;
    }

    /**
     * 构建失败计数的 Redis Key。
     */
    public static String failCountKey(String account) {
        return FAIL_COUNT_PREFIX + account;
    }

    /**
     * 构建锁定标记的 Redis Key。
     */
    public static String lockKey(String account) {
        return LOCK_PREFIX + account;
    }

    /**
     * 构建验证码的 Redis Key。
     */
    public static String captchaKey(String captchaKey) {
        return CAPTCHA_PREFIX + captchaKey;
    }

    /**
     * 构建 Token 黑名单的 Redis Key。
     */
    public static String blacklistKey(String token) {
        return BLACKLIST_PREFIX + token;
    }
}
