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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 当前用户资料。接口文档 5.1.4–5.1.6。
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

    @Operation(summary = "更新头像（上传图片）",
            description = "multipart/form-data，字段 file；只接受 jpg/png/webp，最大 5MB。"
                    + "后端把图片存进 OSS，再把 fileId 写进用户资料，返回最新资料（含 avatarUrl，可直接回填头像框）；旧头像不删除")
    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UserVO> updateAvatar(@RequestPart("file") MultipartFile file) {
        return Result.ok(userService.updateAvatar(UserContext.requireUserId(), file));
    }
}
