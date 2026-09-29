package com.dss.common.web;

import com.dss.common.config.DssProperties;
import com.dss.common.constant.RedisKeys;
import com.dss.common.context.LoginUser;
import com.dss.common.context.UserContext;
import com.dss.common.error.CommonErrorCode;
import com.dss.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 登录拦截器（已实现）：
 * 校验请求头 {@code Authorization: Bearer <token>}，从 Redis 取登录态放进 {@link UserContext}，
 * 并把有效期续满（滑动过期）。公开接口和内部接口不经过它，见 {@link WebMvcConfig}。
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final StringRedisTemplate redisTemplate;
    private final DssProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = resolveToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        String key = RedisKeys.loginToken(token);
        Map<Object, Object> session = redisTemplate.opsForHash().entries(key);
        Object userId = session.get(RedisKeys.TOKEN_FIELD_USER_ID);
        if (userId == null) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED);
        }
        redisTemplate.expire(key, properties.getAuth().getTokenTtl());
        UserContext.set(new LoginUser(
                Long.valueOf(userId.toString()),
                (String) session.get(RedisKeys.TOKEN_FIELD_NICKNAME),
                (String) session.get(RedisKeys.TOKEN_FIELD_AVATAR),
                token));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private String resolveToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED);
        }
        return token;
    }
}
