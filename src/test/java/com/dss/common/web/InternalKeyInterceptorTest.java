package com.dss.common.web;

import com.dss.common.config.DssProperties;
import com.dss.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内部接口鉴权：/api/v1/internal/** 必须带 X-Internal-Key；缺失、错误、服务端没配置口令都返回 403 / 40300。
 * 用户 token 和内部口令不能互相替代（见 WebMvcConfigPathTest 的路径规则）。
 */
class InternalKeyInterceptorTest {

    private static final String KEY = "internal-key-for-test";

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("口令正确：放行")
    void correctKey() {
        InternalKeyInterceptor interceptor = interceptor(KEY);

        assertThat(interceptor.preHandle(requestWith(KEY), response, new Object())).isTrue();
    }

    @Test
    @DisplayName("口令错误：403 / 40300")
    void wrongKey() {
        InternalKeyInterceptor interceptor = interceptor(KEY);

        assertThatThrownBy(() -> interceptor.preHandle(requestWith("wrong-key"), response, new Object()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertForbidden((BizException) e));
    }

    @Test
    @DisplayName("口令是另一个更长的值（前缀相同）：403 / 40300")
    void prefixOfKeyIsRejected() {
        InternalKeyInterceptor interceptor = interceptor(KEY);

        assertThatThrownBy(() -> interceptor.preHandle(requestWith(KEY + "x"), response, new Object()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertForbidden((BizException) e));
    }

    @Test
    @DisplayName("没带请求头：403 / 40300")
    void missingHeader() {
        InternalKeyInterceptor interceptor = interceptor(KEY);

        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest(), response, new Object()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertForbidden((BizException) e));
    }

    @Test
    @DisplayName("空字符串请求头：403 / 40300")
    void emptyHeader() {
        InternalKeyInterceptor interceptor = interceptor(KEY);

        assertThatThrownBy(() -> interceptor.preHandle(requestWith(""), response, new Object()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertForbidden((BizException) e));
    }

    @Test
    @DisplayName("服务端没配置 dss.internal.key：一律拒绝，即使请求头也是空")
    void unconfiguredServerAlwaysRejects() {
        InternalKeyInterceptor interceptor = interceptor(null);

        assertThatThrownBy(() -> interceptor.preHandle(requestWith(""), response, new Object()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertForbidden((BizException) e));
        assertThatThrownBy(() -> interceptor.preHandle(requestWith("anything"), response, new Object()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertForbidden((BizException) e));
    }

    @Test
    @DisplayName("请求头名固定为 X-Internal-Key（Swagger 和文档都按这个名字）")
    void headerName() {
        assertThat(InternalKeyInterceptor.HEADER).isEqualTo("X-Internal-Key");
    }

    private void assertForbidden(BizException e) {
        assertThat(e.getErrorCode().getCode()).isEqualTo(40300);
        assertThat(e.getErrorCode().getHttpStatus()).isEqualTo(403);
        assertThat(e.getErrorCode().getMsg()).isEqualTo("内部口令错误");
    }

    private InternalKeyInterceptor interceptor(String configuredKey) {
        DssProperties properties = new DssProperties();
        properties.getInternal().setKey(configuredKey);
        return new InternalKeyInterceptor(properties);
    }

    private MockHttpServletRequest requestWith(String internalKey) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalKeyInterceptor.HEADER, internalKey);
        return request;
    }
}
