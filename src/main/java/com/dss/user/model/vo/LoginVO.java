package com.dss.user.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录结果")
public class LoginVO {

    @Schema(description = "放进请求头 Authorization: Bearer <token>", example = "3f8c1a52-7d4e-4b9a-9c1e-2a6f0e5d7b21")
    private String token;

    @Schema(description = "用户资料")
    private UserVO user;
}
