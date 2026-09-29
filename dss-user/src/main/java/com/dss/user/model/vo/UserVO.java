package com.dss.user.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "用户资料")
public class UserVO {

    @Schema(description = "用户 ID（字符串）", example = "101486542963081217")
    private String id;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "昵称", example = "用户8000")
    private String nickname;

    @Schema(description = "头像 fileId，可为 null")
    private String avatar;

    @Schema(description = "头像完整 URL，可为 null")
    private String avatarUrl;

    @Schema(description = "注册时间", example = "2026-10-01 12:00:00")
    private LocalDateTime createTime;
}
