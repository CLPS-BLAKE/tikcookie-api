package com.dss.favorite.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dss.common.error.CommonErrorCode;
import com.dss.common.exception.BizException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.result.PageResult;
import com.dss.favorite.mapper.FavoriteMapper;
import com.dss.favorite.model.dto.FavoriteDTO;
import com.dss.favorite.model.dto.FavoriteQuery;
import com.dss.favorite.model.entity.Favorite;
import com.dss.favorite.model.enums.FavoriteErrorCode;
import com.dss.favorite.model.enums.TargetType;
import com.dss.favorite.model.vo.FavoriteStatusVO;
import com.dss.favorite.model.vo.FavoriteVO;
import com.dss.favorite.service.FavoriteService;
import com.dss.product.model.entity.Product;
import com.dss.product.model.enums.ProductStatus;
import com.dss.product.service.ProductService;
import com.dss.shop.model.entity.Shop;
import com.dss.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 收藏实现。规则见接口文档 5.7、{@link FavoriteService} 各方法注释。
 * <p>
 * add / remove 都不加事务：add 只有一条 insert（并发重复由唯一索引 uk_favorites_user_target 兜底，
 * 照 AuthServiceImpl 的写法捕获 DuplicateKeyException 转 105002）；remove 只有一条 delete。
 */
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ShopService shopService;
    private final ProductService productService;
    private final FileUrlResolver fileUrlResolver;

    @Override
    public void add(Long userId, FavoriteDTO dto) {
        Long targetId = parseId(dto.getTargetId());
        requireTargetExists(dto.getTargetType(), targetId);
        if (isFavorited(userId, dto.getTargetType(), targetId)) {
            throw new BizException(FavoriteErrorCode.FAVORITE_ALREADY_EXISTS);
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTargetType(dto.getTargetType());
        favorite.setTargetId(targetId);
        // createdAt 由 MybatisPlusConfig 的 MetaObjectHandler 在 insert 时填充
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            // 先查后插之间被并发请求抢先，uk_favorites_user_target 兜底
            throw new BizException(FavoriteErrorCode.FAVORITE_ALREADY_EXISTS);
        }
    }

    @Override
    public void remove(Long userId, TargetType targetType, Long targetId) {
        favoriteMapper.delete(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, targetType)
                .eq(Favorite::getTargetId, targetId));
        // 没收藏过也返回成功（影响行数为 0 不管）
    }

    @Override
    public PageResult<FavoriteVO> list(Long userId, FavoriteQuery query) {
        Page<Favorite> page = favoriteMapper.selectPage(Page.of(query.getPage(), query.getSize()),
                Wrappers.<Favorite>lambdaQuery()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getTargetType, query.getTargetType())
                        .orderByDesc(Favorite::getCreatedAt)
                        .orderByDesc(Favorite::getId));
        return PageResult.of(toVOs(page.getRecords(), query.getTargetType()),
                page.getTotal(), query.getPage(), query.getSize());
    }

    @Override
    public FavoriteStatusVO status(Long userId, TargetType targetType, Long targetId) {
        return new FavoriteStatusVO(isFavorited(userId, targetType, targetId));
    }

    // ---------- private ----------

    /**
     * 目标必须存在（105001）。用 getRequiredShop / getRequiredProduct 校验：下架商品也算存在。
     * 依赖模块抛的 102001 / 103001 统一翻译成本模块的 105001（接口文档 5.7.1）。
     */
    private void requireTargetExists(TargetType targetType, Long targetId) {
        try {
            if (targetType == TargetType.SHOP) {
                shopService.getRequiredShop(targetId);
            } else {
                productService.getRequiredProduct(targetId);
            }
        } catch (BizException e) {
            throw new BizException(FavoriteErrorCode.FAVORITE_TARGET_NOT_FOUND);
        }
    }

    private boolean isFavorited(Long userId, TargetType targetType, Long targetId) {
        Long count = favoriteMapper.selectCount(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, targetType)
                .eq(Favorite::getTargetId, targetId));
        return count != null && count > 0;
    }

    private Long parseId(String raw) {
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "targetId 必须是数字");
        }
    }

    /** 批量补名称和图片，避免 N+1；收藏的目标已被删除时照常输出该行，名称为空、商品按下架处理。 */
    private List<FavoriteVO> toVOs(List<Favorite> favorites, TargetType targetType) {
        if (favorites == null || favorites.isEmpty()) {
            return List.of();
        }
        List<Long> targetIds = favorites.stream().map(Favorite::getTargetId).distinct().toList();
        Map<Long, Shop> shops = targetType == TargetType.SHOP
                ? shopService.getShopsByIds(targetIds) : Map.of();
        Map<Long, Product> products = targetType == TargetType.PRODUCT
                ? productService.getProductsByIds(targetIds) : Map.of();

        List<FavoriteVO> list = new ArrayList<>(favorites.size());
        for (Favorite favorite : favorites) {
            FavoriteVO vo = new FavoriteVO();
            vo.setTargetType(favorite.getTargetType());
            vo.setTargetId(String.valueOf(favorite.getTargetId()));
            vo.setCreateTime(favorite.getCreatedAt());
            if (targetType == TargetType.SHOP) {
                Shop shop = shops.get(favorite.getTargetId());
                vo.setName(shop == null ? null : shop.getName());
                vo.setImageUrl(shop == null || shop.getImages() == null || shop.getImages().isEmpty()
                        ? null : fileUrlResolver.toUrl(shop.getImages().get(0)));
            } else {
                Product product = products.get(favorite.getTargetId());
                if (product == null) {
                    vo.setOffShelf(true);
                } else {
                    vo.setName(product.getName());
                    vo.setImageUrl(fileUrlResolver.toUrl(product.getImage()));
                    vo.setPrice(product.getPrice());
                    vo.setOffShelf(product.getStatus() != ProductStatus.ON_SHELF);
                }
            }
            list.add(vo);
        }
        return list;
    }
}
