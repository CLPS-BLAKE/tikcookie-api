package com.dss.common.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前请求的登录用户，由登录拦截器从 Redis 登录态里取出后放进 {@link UserContext}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private Long userId;

    private String nickname;

    /** 头像 fileId，可能为 null。 */
    private String avatar;

    /** 本次请求使用的 token（登出时用）。 */
    private String token;
}
