package com.dss.shop.service.impl;

import com.dss.common.exception.NotImplementedException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.id.RedisIdGenerator;
import com.dss.common.mq.SearchSyncPublisher;
import com.dss.shop.model.dto.ShopSaveDTO;
import com.dss.shop.model.entity.Shop;
import com.dss.shop.model.vo.CategoryVO;
import com.dss.shop.model.vo.ShopVO;
import com.dss.shop.repository.ShopRepository;
import com.dss.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 店铺实现（骨架期是桩）。
 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ShopRepository shopRepository;
    private final RedisIdGenerator idGenerator;
    private final SearchSyncPublisher searchSyncPublisher;
    private final FileUrlResolver fileUrlResolver;

    @Override
    public List<CategoryVO> listCategories() {
        throw new NotImplementedException();
    }

    @Override
    public ShopVO getShop(Long shopId) {
        throw new NotImplementedException();
    }

    @Override
    public ShopVO createShop(ShopSaveDTO dto) {
        throw new NotImplementedException();
    }

    @Override
    public ShopVO updateShop(Long shopId, ShopSaveDTO dto) {
        throw new NotImplementedException();
    }

    @Override
    public Shop getRequiredShop(Long shopId) {
        throw new NotImplementedException();
    }

    @Override
    public Map<Long, Shop> getShopsByIds(Collection<Long> shopIds) {
        throw new NotImplementedException();
    }
}
