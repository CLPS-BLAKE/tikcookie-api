package com.dss.favorite.model.entity;

import com.dss.favorite.model.enums.TargetType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 收藏（集合 favorites）。(userId, targetType, targetId) 唯一。字段说明见 docs/中间件配置.md 4.6。
 */
@Data
@Document(collection = "favorites")
public class Favorite {

    /** 收藏 ID，RedisIdGenerator 生成（biz=favorite）。 */
    @Id
    private Long id;

    private Long userId;

    private TargetType targetType;

    /** 店铺 ID 或商品 ID。 */
    private Long targetId;

    private LocalDateTime createTime;
}
