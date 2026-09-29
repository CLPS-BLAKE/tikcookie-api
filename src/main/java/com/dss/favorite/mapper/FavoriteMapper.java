package com.dss.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dss.favorite.model.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * favorites 表。是否已收藏、取消收藏按 (userId, targetType, targetId) 查（uk_favorites_user_target）；
 * 收藏列表按 createdAt 倒序分页（idx_favorites_user_type_created）。
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
