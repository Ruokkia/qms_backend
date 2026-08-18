package com.kangli.qms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.kangli.qms.security.PermissionInterceptor;

import java.nio.file.Paths;
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

    @Value("${qms.file-storage.base-dir:./uploads}")
    private String fileStorageBaseDir;

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
            "/error",
            "/files/**"
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

    /** 上传文件静态资源映射：/files/** → 本地存储目录 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(fileStorageBaseDir).toAbsolutePath().normalize().toString() + java.io.File.separator;
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + location);
    }
}
