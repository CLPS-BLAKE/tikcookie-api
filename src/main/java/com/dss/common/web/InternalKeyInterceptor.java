package com.dss.common.web;

import com.dss.common.config.DssProperties;
import com.dss.common.error.CommonErrorCode;
import com.dss.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部口令拦截器（已实现）：{@code /api/v1/internal/**} 必须带请求头 X-Internal-Key，
 * 值和配置 dss.internal.key 一致才放行；没配置口令时一律拒绝（403 / 40300）。
 */
@Component
@RequiredArgsConstructor
public class InternalKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Internal-Key";

    private final DssProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String expected = properties.getInternal().getKey();
        String actual = request.getHeader(HEADER);
        if (!StringUtils.hasText(expected) || actual == null
                // 恒定时间比较，避免按响应时间猜口令
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new BizException(CommonErrorCode.INTERNAL_KEY_INVALID);
        }
        return true;
    }
}
