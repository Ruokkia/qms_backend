package com.kangli.qms.common;

/**
 * 当前登录用户 ThreadLocal 持有器。
 * <p>由 {@link com.kangli.qms.config.JwtInterceptor} 写入/清除，
 * Controller/Service 通过 {@link #get()} 获取当前用户。</p>
 */
public final class LoginUserHolder {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private LoginUserHolder() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
