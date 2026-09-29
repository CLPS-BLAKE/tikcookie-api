package com.dss.product.service.impl;

import com.dss.common.exception.NotImplementedException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.id.RedisIdGenerator;
import com.dss.common.mq.SearchSyncPublisher;
import com.dss.common.result.PageResult;
import com.dss.product.model.dto.ProductFeedQuery;
import com.dss.product.model.dto.ProductSaveDTO;
import com.dss.product.model.entity.Product;
import com.dss.product.model.enums.ProductStatus;
import com.dss.product.model.vo.FlashProductVO;
import com.dss.product.model.vo.ProductCardVO;
import com.dss.product.model.vo.ProductVO;
import com.dss.product.repository.ProductRepository;
import com.dss.product.service.ProductService;
import com.dss.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 商品实现（骨架期是桩）。
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ShopService shopService;
    private final RedisIdGenerator idGenerator;
    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final SearchSyncPublisher searchSyncPublisher;
    private final FileUrlResolver fileUrlResolver;

    @Override
    public List<ProductCardVO> listShopProducts(Long shopId) {
        throw new NotImplementedException();
    }

    @Override
    public ProductVO getProduct(Long productId) {
        throw new NotImplementedException();
    }

    @Override
    public PageResult<ProductCardVO> listFeed(ProductFeedQuery query) {
        throw new NotImplementedException();
    }

    @Override
    public List<FlashProductVO> listFlash() {
        throw new NotImplementedException();
    }

    @Override
    public ProductVO createProduct(ProductSaveDTO dto) {
        throw new NotImplementedException();
    }

    @Override
    public ProductVO updateProduct(Long productId, ProductSaveDTO dto) {
        throw new NotImplementedException();
    }

    @Override
    public void changeStatus(Long productId, ProductStatus status) {
        throw new NotImplementedException();
    }

    @Override
    public Product getRequiredProduct(Long productId) {
        throw new NotImplementedException();
    }

    @Override
    public Map<Long, Product> getProductsByIds(Collection<Long> productIds) {
        throw new NotImplementedException();
    }

    @Override
    public void changeSoldCount(Long productId, int delta) {
        throw new NotImplementedException();
    }

    @Override
    public boolean decreaseFlashStock(Long productId) {
        throw new NotImplementedException();
    }

    @Override
    public void increaseFlashStock(Long productId) {
        throw new NotImplementedException();
    }
}
