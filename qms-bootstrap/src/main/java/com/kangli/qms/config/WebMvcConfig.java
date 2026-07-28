package com.kangli.qms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.kangli.qms.security.PermissionInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * Web MVC 配置 — 注册 JWT 拦截器。
 * <p>排除登录/刷新/Swagger 等公开路径。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final PermissionInterceptor permissionInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor, PermissionInterceptor permissionInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
        this.permissionInterceptor = permissionInterceptor;
    }

    /** 不需要认证的路径（Ant 风格） */
    private static final List<String> EXCLUDE_PATHS = Arrays.asList(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/captcha",
            "/api/v1/auth/directory",
            "/doc.html",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/v2/api-docs",
            "/v3/api-docs",
            "/webjars/**",
            "/favicon.ico",
            "/error"
    );

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXCLUDE_PATHS);
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(EXCLUDE_PATHS);
    }
}
