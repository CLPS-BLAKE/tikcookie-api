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
            "/api/v1/search/**"
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
