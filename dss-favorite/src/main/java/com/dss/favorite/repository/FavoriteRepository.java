package com.dss.favorite.repository;

import com.dss.favorite.model.entity.Favorite;
import com.dss.favorite.model.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * favorites 集合。索引 uk_user_target、idx_user_type_createTime 由 docs/中间件配置.md 4.7 的建结构脚本创建。
 */
public interface FavoriteRepository extends MongoRepository<Favorite, Long> {

    /** 是否已收藏（uk_user_target）。 */
    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, TargetType targetType, Long targetId);

    /** 取消收藏（uk_user_target）。 */
    void deleteByUserIdAndTargetTypeAndTargetId(Long userId, TargetType targetType, Long targetId);

    /** 收藏列表（idx_user_type_createTime）。 */
    Page<Favorite> findByUserIdAndTargetTypeOrderByCreateTimeDesc(Long userId, TargetType targetType, Pageable pageable);
}
