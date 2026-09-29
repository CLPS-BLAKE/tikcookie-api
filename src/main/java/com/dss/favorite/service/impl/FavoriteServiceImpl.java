package com.dss.favorite.service.impl;

import com.dss.common.exception.NotImplementedException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.result.PageResult;
import com.dss.favorite.mapper.FavoriteMapper;
import com.dss.favorite.model.dto.FavoriteDTO;
import com.dss.favorite.model.dto.FavoriteQuery;
import com.dss.favorite.model.enums.TargetType;
import com.dss.favorite.model.vo.FavoriteStatusVO;
import com.dss.favorite.model.vo.FavoriteVO;
import com.dss.favorite.service.FavoriteService;
import com.dss.product.service.ProductService;
import com.dss.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 收藏实现（骨架期是桩）。
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
        throw new NotImplementedException();
    }

    @Override
    public void remove(Long userId, TargetType targetType, Long targetId) {
        throw new NotImplementedException();
    }

    @Override
    public PageResult<FavoriteVO> list(Long userId, FavoriteQuery query) {
        throw new NotImplementedException();
    }

    @Override
    public FavoriteStatusVO status(Long userId, TargetType targetType, Long targetId) {
        throw new NotImplementedException();
    }
}
