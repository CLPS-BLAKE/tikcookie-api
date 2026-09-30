package com.dss.common.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册拦截器：
 * - 内部接口 /api/v1/internal/** 只校验 X-Internal-Key；
 * - 其余 /api/v1/** 除了 {@link #PUBLIC_PATHS} 都要登录。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    public static final String INTERNAL_PATH = "/api/v1/internal/**";

    /** 不需要登录的 C 端接口（接口文档里标"公开"的）。 */
    public static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/sms-code",
            "/api/v1/auth/login",
            "/api/v1/categories",
            "/api/v1/shops/**",
            "/api/v1/products/**",
            "/api/v1/search/**",
            // 读图：Bucket 公共读时前端直连 OSS 域名；这个代理接口是备用路径（私有读场景），
            // <img src> 带不了 Authorization 头，所以必须公开。
            // 注意上传入口 /api/v1/files/images 不在这里，它仍然要求登录。
            "/api/v1/images/**"
    };

    private final LoginInterceptor loginInterceptor;
    private final InternalKeyInterceptor internalKeyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalKeyInterceptor)
                .addPathPatterns(INTERNAL_PATH);
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(INTERNAL_PATH)
                .excludePathPatterns(PUBLIC_PATHS);
    }
}
