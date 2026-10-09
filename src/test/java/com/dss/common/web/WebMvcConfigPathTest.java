package com.dss.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 接口放行规则（接口文档第 6 节的鉴权列）：
 * 公开接口不校验 token；其余 /api/v1/** 要登录；/api/v1/internal/** 只校验 X-Internal-Key。
 * 这里用 Spring 自己的 AntPathMatcher 校验黑白名单，尤其是两个上传入口不能被误放行。
 */
class WebMvcConfigPathTest {

    private final PathMatcher matcher = new AntPathMatcher();

    @Test
    @DisplayName("文档中标为「公开」的接口都在放行名单里，且不需要 token")
    void publicEndpointsAreOpen() {
        assertThat(Arrays.asList(
                "/api/v1/auth/sms-code",
                "/api/v1/auth/login",
                "/api/v1/categories",
                "/api/v1/shops/1",
                "/api/v1/shops/1/products",
                "/api/v1/products",
                "/api/v1/products/flash",
                "/api/v1/products/1",
                "/api/v1/search/products",
                "/api/v1/search/shops",
                // 读图公开：<img src> 带不了 Authorization 头，图片由后端代理输出
                "/api/v1/images/group1/M00/00/00/0123456789abcdef0123456789abcdef.jpg"))
                .allSatisfy(path -> assertThat(isPublic(path)).as(path).isTrue());
    }

    @Test
    @DisplayName("要登录的接口不在放行名单里")
    void protectedEndpointsAreNotOpen() {
        assertThat(Arrays.asList(
                "/api/v1/auth/logout",
                "/api/v1/users/me",
                "/api/v1/orders",
                "/api/v1/orders/1/pay",
                "/api/v1/favorites",
                "/api/v1/favorites/status"))
                .allSatisfy(path -> assertThat(isPublic(path)).as(path).isFalse());
    }

    @Test
    @DisplayName("上传入口：C 端要 Bearer token，内部要 X-Internal-Key，都不在公开名单里")
    void uploadEndpointsAreNotPublic() {
        assertThat(isPublic("/api/v1/files/images")).isFalse();
        assertThat(isPublic("/api/v1/internal/files/images")).isFalse();
    }

    @Test
    @DisplayName("读图和上传是两条路径：只有读图公开，上传仍要登录")
    void readIsPublicButUploadIsNot() {
        // 同一个前缀 /api/v1/images 下才是公开的读图接口
        assertThat(isPublic("/api/v1/images/group1/M00/00/00/a.jpg")).isTrue();
        assertThat(isPublic("/api/v1/images")).isTrue();
        // 上传路径前缀不同（/files/images），不会被公开规则误放行
        assertThat(isPublic("/api/v1/files/images")).isFalse();
        assertThat(isPublic("/api/v1/files/images/whatever")).isFalse();
    }

    @Test
    @DisplayName("内部路径全部由内部口令拦截器覆盖，且不被登录拦截器拦截")
    void internalPaths() {
        assertThat(Arrays.asList(
                "/api/v1/internal/files/images",
                "/api/v1/internal/shops",
                "/api/v1/internal/shops/1",
                "/api/v1/internal/products",
                "/api/v1/internal/products/1",
                "/api/v1/internal/products/1/status"))
                .allSatisfy(path -> assertThat(matcher.match(WebMvcConfig.INTERNAL_PATH, path)).as(path).isTrue());

        // 内部前缀下的路径同时也不在公开名单里，避免"公开 + 内部"两套规则互相覆盖
        assertThat(Arrays.asList(
                "/api/v1/internal/files/images",
                "/api/v1/internal/shops",
                "/api/v1/internal/products"))
                .allSatisfy(path -> assertThat(isPublic(path)).as(path).isFalse());
    }

    @Test
    @DisplayName("C 端上传接口不是内部路径（否则会被要求带内部口令）")
    void consumerUploadIsNotInternal() {
        assertThat(matcher.match(WebMvcConfig.INTERNAL_PATH, "/api/v1/files/images")).isFalse();
    }

    @Test
    @DisplayName("注册两个拦截器：登录拦 /api/v1/**，内部口令拦 /api/v1/internal/**")
    void registersBothInterceptors() {
        ExposedRegistry registry = new ExposedRegistry();

        new WebMvcConfig(mock(LoginInterceptor.class), mock(InternalKeyInterceptor.class)).addInterceptors(registry);

        assertThat(registry.registered()).hasSize(2);
    }

    private boolean isPublic(String path) {
        return Arrays.stream(WebMvcConfig.PUBLIC_PATHS).anyMatch(pattern -> matcher.match(pattern, path));
    }

    /** getInterceptors() 是 protected，测试里暴露出来只为了数一下注册了几个拦截器。 */
    private static class ExposedRegistry extends InterceptorRegistry {

        List<Object> registered() {
            return getInterceptors();
        }
    }
}
