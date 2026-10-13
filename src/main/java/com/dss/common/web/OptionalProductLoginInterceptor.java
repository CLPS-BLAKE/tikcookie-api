package com.dss.common.web;

import com.dss.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/** Product browsing stays public; a supplied token must be validated before reading user quota. */
@RequiredArgsConstructor
public class OptionalProductLoginInterceptor implements HandlerInterceptor {
    private final LoginInterceptor loginInterceptor;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
        // Personalized detail responses must never be served from a shared browser/proxy cache.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        if (!StringUtils.hasText(request.getHeader(HttpHeaders.AUTHORIZATION))) return true;
        return loginInterceptor.preHandle(request, response, handler);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
