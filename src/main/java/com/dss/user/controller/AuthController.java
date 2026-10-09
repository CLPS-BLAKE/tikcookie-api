package com.dss.user.controller;

import com.dss.common.config.OpenApiConfig;
import com.dss.common.context.UserContext;
import com.dss.common.result.Result;
import com.dss.user.model.dto.LoginDTO;
import com.dss.user.model.dto.SmsCodeDTO;
import com.dss.user.model.vo.LoginVO;
import com.dss.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证：发验证码、登录（自动注册）、登出。接口文档 5.1.1–5.1.3。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "发送验证码（开发环境）",
            description = "验证码存入 Redis 后，同时通过接口返回；同一手机号 60 秒内不能重发")
    @PostMapping("/sms-code")
    public Result<String> sendSmsCode(@Valid @RequestBody SmsCodeDTO dto) {
        String code = authService.sendSmsCode(dto.getPhone());
        return Result.ok(code);
    }

    @Operation(summary = "登录 / 自动注册（公开）", description = "手机号 + 验证码；新手机号自动注册；返回 token 和用户资料")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @Operation(summary = "登出", description = "删除当前 token 的登录态")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(UserContext.get().getToken());
        return Result.ok();
    }
}
