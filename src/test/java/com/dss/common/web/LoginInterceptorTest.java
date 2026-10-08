package com.dss.common.web;

import com.dss.common.config.DssProperties;
import com.dss.common.constant.RedisKeys;
import com.dss.common.context.UserContext;
import com.dss.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 登录鉴权：Bearer token → Redis 登录态 → UserContext；7 天滑动续期；无效 token 401 / 40100；
 * 请求结束（含异常）清理 ThreadLocal。Redis 用 mock，不依赖真实中间件。
 */
class LoginInterceptorTest {

    private static final String TOKEN = "abcdef0123456789";
    private static final String KEY = RedisKeys.loginToken(TOKEN);

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private DssProperties properties;
    private LoginInterceptor interceptor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        doReturn(hashOperations).when(redisTemplate).opsForHash();
        properties = new DssProperties();
        interceptor = new LoginInterceptor(redisTemplate, properties);
        UserContext.clear();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("有效 token：放行、写入 UserContext、并把 7 天有效期续满")
    void validToken() {
        doReturn(Map.of(
                RedisKeys.TOKEN_FIELD_USER_ID, "7",
                RedisKeys.TOKEN_FIELD_NICKNAME, "小王",
                RedisKeys.TOKEN_FIELD_AVATAR, "group1/M00/00/00/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg"))
                .when(hashOperations).entries(KEY);
        MockHttpServletRequest request = requestWith("Bearer " + TOKEN);

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();

        assertThat(UserContext.get()).isNotNull();
        assertThat(UserContext.get().getUserId()).isEqualTo(7L);
        assertThat(UserContext.get().getNickname()).isEqualTo("小王");
        assertThat(UserContext.get().getToken()).isEqualTo(TOKEN);
        assertThat(UserContext.requireUserId()).isEqualTo(7L);
        verify(redisTemplate).expire(KEY, Duration.ofDays(7));
    }

    @Test
    @DisplayName("续期用的是配置里的 token-ttl，不是写死的 7 天")
    void renewalUsesConfiguredTtl() {
        properties.getAuth().setTokenTtl(Duration.ofHours(3));
        doReturn(Map.of(RedisKeys.TOKEN_FIELD_USER_ID, "9")).when(hashOperations).entries(KEY);

        interceptor.preHandle(requestWith("Bearer " + TOKEN), new MockHttpServletResponse(), new Object());

        verify(redisTemplate).expire(KEY, Duration.ofHours(3));
    }

    @Test
    @DisplayName("请求结束（含异常）后清理 ThreadLocal，不留用户上下文")
    void clearsContextAfterCompletion() {
        doReturn(Map.of(RedisKeys.TOKEN_FIELD_USER_ID, "7")).when(hashOperations).entries(KEY);
        MockHttpServletRequest request = requestWith("Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());

        // 业务方法抛异常时，Spring 仍会调用 afterCompletion
        interceptor.afterCompletion(request, response, new Object(), new IllegalStateException("业务失败"));

        assertThat(UserContext.get()).isNull();
    }

    @Test
    @DisplayName("没有 Authorization 头：401 / 40100，且不查 Redis")
    void missingHeader() {
        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(40100))
                .satisfies(e -> assertThat(((BizException) e).getErrorCode().getHttpStatus()).isEqualTo(401));

        verifyNoInteractions(redisTemplate);
        assertThat(UserContext.get()).isNull();
    }

    @Test
    @DisplayName("不是 Bearer 方案（如 Basic）：401 / 40100")
    void wrongScheme() {
        MockHttpServletRequest request = requestWith("Basic " + TOKEN);

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(40100));

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Bearer 后面是空的：401 / 40100")
    void blankToken() {
        assertThatThrownBy(() -> interceptor.preHandle(requestWith("Bearer    "), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(40100));
    }

    @Test
    @DisplayName("token 不在 Redis 里（未登录或已过期）：401 / 40100，不续期")
    void tokenNotInRedis() {
        doReturn(Map.of()).when(hashOperations).entries(KEY);

        assertThatThrownBy(() -> interceptor.preHandle(requestWith("Bearer " + TOKEN), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(40100));

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        assertThat(UserContext.get()).isNull();
    }

    @Test
    @DisplayName("登录态缺少 userId 字段：401 / 40100")
    void sessionWithoutUserId() {
        doReturn(Map.of(RedisKeys.TOKEN_FIELD_NICKNAME, "小王")).when(hashOperations).entries(KEY);

        assertThatThrownBy(() -> interceptor.preHandle(requestWith("Bearer " + TOKEN), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(40100));
    }

    @Test
    @DisplayName("未登录时 requireUserId 抛 401 / 40100（公开接口里 get() 为 null）")
    void requireUserIdWithoutLogin() {
        assertThat(UserContext.get()).isNull();
        assertThatThrownBy(UserContext::requireUserId)
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(40100));
    }

    private MockHttpServletRequest requestWith(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        return request;
    }
}
