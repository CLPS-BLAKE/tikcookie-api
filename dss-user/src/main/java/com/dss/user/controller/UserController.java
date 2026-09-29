package com.dss.user.controller;

import com.dss.common.config.OpenApiConfig;
import com.dss.common.context.UserContext;
import com.dss.common.result.Result;
import com.dss.user.model.dto.UpdateProfileDTO;
import com.dss.user.model.vo.UserVO;
import com.dss.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户资料。接口文档 5.1.4–5.1.5。
 */
@Tag(name = "用户")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class UserController {

    private final UserService userService;

    @Operation(summary = "当前用户资料")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.getCurrentUser(UserContext.requireUserId()));
    }

    @Operation(summary = "修改昵称 / 头像", description = "传哪个字段就改哪个；头像传上传接口返回的 fileId")
    @PutMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody UpdateProfileDTO dto) {
        return Result.ok(userService.updateProfile(UserContext.requireUserId(), dto));
    }
}
