package com.dss.user.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dss.user.model.enums.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户（表 users）。验证码和登录态不在这里，都在 Redis。表结构见 dss-init.sql。
 */
@Data
@TableName("users")
public class User {

    /** 用户 ID，MySQL 自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号，唯一（uk_users_phone）。 */
    private String phone;

    /** 昵称（即最初设计里的"账户名"）。 */
    private String nickname;

    /** 头像 fileId（OSS ObjectKey），可为空。 */
    private String avatar;

    private UserStatus status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
