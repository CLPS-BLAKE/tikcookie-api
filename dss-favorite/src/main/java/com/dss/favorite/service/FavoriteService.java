package com.dss.favorite.service;

import com.dss.common.result.PageResult;
import com.dss.favorite.model.dto.FavoriteDTO;
import com.dss.favorite.model.dto.FavoriteQuery;
import com.dss.favorite.model.enums.TargetType;
import com.dss.favorite.model.vo.FavoriteStatusVO;
import com.dss.favorite.model.vo.FavoriteVO;

/**
 * 收藏。规则见需求文档 6.6。
 */
public interface FavoriteService {

    /**
     * 收藏：目标不存在 105001（用 ShopService / ProductService 查，下架商品也算存在）；
     * 已收藏 105002（先 exists 查；并发下由唯一索引 uk_user_target 兜底，插入冲突也返回 105002）。
     * ID 用 RedisIdGenerator（biz=favorite）。
     */
    void add(Long userId, FavoriteDTO dto);

    /**
     * 取消收藏：按 (userId, targetType, targetId) 删除；没收藏过也算成功。
     */
    void remove(Long userId, TargetType targetType, Long targetId);

    /**
     * 收藏列表：按 createTime 倒序分页；用 getShopsByIds / getProductsByIds 批量补名称和图片；
     * 商品已下架时 offShelf = true。
     */
    PageResult<FavoriteVO> list(Long userId, FavoriteQuery query);

    /**
     * 是否已收藏。
     */
    FavoriteStatusVO status(Long userId, TargetType targetType, Long targetId);
}
