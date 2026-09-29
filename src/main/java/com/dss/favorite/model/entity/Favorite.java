package com.dss.favorite.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dss.favorite.model.enums.TargetType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏（表 favorites）。(userId, targetType, targetId) 唯一（uk_favorites_user_target）。表结构见 dss-init.sql。
 */
@Data
@TableName("favorites")
public class Favorite {

    /** 收藏 ID，MySQL 自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private TargetType targetType;

    /** 店铺 ID 或商品 ID。 */
    private Long targetId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
