package com.dss.user.model.entity;

import com.dss.user.model.enums.UserStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 用户（集合 users）。验证码和登录态不在这里，都在 Redis。字段说明见 docs/中间件配置.md 4.2。
 */
@Data
@Document(collection = "users")
public class User {

    /** 用户 ID，RedisIdGenerator 生成（biz=user）。 */
    @Id
    private Long id;

    /** 手机号，唯一。 */
    private String phone;

    /** 昵称（即最初设计里的"账户名"）。 */
    private String nickname;

    /** 头像 fileId，可为空。 */
    private String avatar;

    private UserStatus status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
