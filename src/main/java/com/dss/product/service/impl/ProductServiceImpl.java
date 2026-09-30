package com.dss.product.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dss.common.exception.BizException;
import com.dss.common.error.CommonErrorCode;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.mq.SearchSyncPublisher;
import com.dss.common.result.PageResult;
import com.dss.product.mapper.ProductMapper;
import com.dss.product.model.dto.ProductFeedQuery;
import com.dss.product.model.dto.ProductSaveDTO;
import com.dss.product.model.entity.Product;
import com.dss.product.model.enums.FeedSort;
import com.dss.product.model.enums.ProductErrorCode;
import com.dss.product.model.enums.ProductStatus;
import com.dss.product.model.enums.ProductType;
import com.dss.product.model.vo.FlashProductVO;
import com.dss.product.model.vo.ProductCardVO;
import com.dss.product.model.vo.ProductVO;
import com.dss.product.service.ProductService;
import com.dss.shop.model.entity.Shop;
import com.dss.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品实现。读接口的过滤与排序规则见 ProductService 各方法注释。
 * <p>
 * 写操作：会改数据的三个内部接口加 @Transactional，提交后由 {@link SearchSyncPublisher} 发 product.changed；
 * 供订单模块调用的 lockProduct / changeSoldCount / decreaseFlashStock / increaseFlashStock 不开事务，
 * 它们必须跑在调用方（订单）的事务里。
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ShopService shopService;
    private final SearchSyncPublisher searchSyncPublisher;
    private final FileUrlResolver fileUrlResolver;

    // ---------- 公开查询 ----------

    @Override
    public List<ProductCardVO> listShopProducts(Long shopId) {
        Shop shop = shopService.getRequiredShop(shopId);
        List<Product> products = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getShopId, shopId)
                .eq(Product::getStatus, ProductStatus.ON_SHELF)
                .orderByDesc(Product::getCreatedAt)
                .orderByDesc(Product::getId));
        return toCardVOs(products, Map.of(shopId, shop));
    }

    @Override
    public ProductVO getProduct(Long productId) {
        Product product = getRequiredProduct(productId);
        if (product.getStatus() != ProductStatus.ON_SHELF) {
            throw new BizException(ProductErrorCode.PRODUCT_OFF_SHELF);
        }
        return toProductVO(product, shopService.getRequiredShop(product.getShopId()));
    }

    @Override
    public PageResult<ProductCardVO> listFeed(ProductFeedQuery query) {
        var wrapper = Wrappers.<Product>lambdaQuery().eq(Product::getStatus, ProductStatus.ON_SHELF);
        if (FeedSort.SALES == FeedSort.fromCode(query.getSort())) {
            wrapper.orderByDesc(Product::getSoldCount).orderByDesc(Product::getId);
        } else {
            wrapper.orderByDesc(Product::getCreatedAt).orderByDesc(Product::getId);
        }
        Page<Product> page = productMapper.selectPage(Page.of(query.getPage(), query.getSize()), wrapper);
        return PageResult.of(toCardVOs(page.getRecords()), page.getTotal(), query.getPage(), query.getSize());
    }

    @Override
    public List<FlashProductVO> listFlash() {
        LocalDateTime now = LocalDateTime.now();
        List<Product> products = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getType, ProductType.FLASH)
                .eq(Product::getStatus, ProductStatus.ON_SHELF)
                .gt(Product::getFlashEndTime, now)
                .orderByAsc(Product::getFlashStartTime)
                .orderByAsc(Product::getId));
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, Shop> shops = loadShops(products);
        List<FlashProductVO> list = new ArrayList<>(products.size());
        for (Product product : products) {
            Shop shop = shops.get(product.getShopId());
            FlashProductVO vo = new FlashProductVO();
            vo.setId(String.valueOf(product.getId()));
            vo.setShopId(String.valueOf(product.getShopId()));
            vo.setShopName(shop == null ? null : shop.getName());
            vo.setName(product.getName());
            vo.setPrice(product.getPrice());
            vo.setImageUrl(fileUrlResolver.toUrl(product.getImage()));
            // 剩余库存以 MySQL products.stock 为准，展示时可能随并发下单变化
            vo.setRemainingStock(product.getStock());
            vo.setFlashStartTime(product.getFlashStartTime());
            vo.setFlashEndTime(product.getFlashEndTime());
            vo.setLimitPerUser(product.getLimitPerUser());
            vo.setOngoing(product.getFlashStartTime() != null && !now.isBefore(product.getFlashStartTime()));
            list.add(vo);
        }
        return list;
    }

    // ---------- 内部录入 ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductVO createProduct(ProductSaveDTO dto) {
        Long shopId = parseId(dto.getShopId(), "shopId");
        shopService.getRequiredShop(shopId);

        Product product = new Product();
        product.setShopId(shopId);
        product.setName(dto.getName());
        product.setContents(dto.getContents());
        product.setPrice(dto.getPrice());
        product.setType(dto.getType());
        product.setImage(dto.getImage());
        product.setValidDays(dto.getValidDays());
        product.setUseRules(dto.getUseRules());
        // 新建后直接上架；已售数从 0 开始
        product.setStatus(ProductStatus.ON_SHELF);
        product.setSoldCount(0);
        applyFlashFields(product, dto);
        productMapper.insert(product);

        searchSyncPublisher.publishProductChanged(product.getId());
        return toProductVO(product, shopService.getRequiredShop(shopId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductVO updateProduct(Long productId, ProductSaveDTO dto) {
        Product current = getRequiredProduct(productId);
        Long shopId = parseId(dto.getShopId(), "shopId");
        if (current.getType() != dto.getType()) {
            throw new BizException(ProductErrorCode.PRODUCT_TYPE_IMMUTABLE);
        }
        if (!Objects.equals(current.getShopId(), shopId)) {
            throw new BizException(ProductErrorCode.PRODUCT_SHOP_IMMUTABLE);
        }
        shopService.getRequiredShop(shopId);

        Product update = new Product();
        update.setId(productId);
        update.setShopId(shopId);
        update.setName(dto.getName());
        update.setContents(dto.getContents());
        update.setPrice(dto.getPrice());
        update.setType(dto.getType());
        update.setImage(dto.getImage());
        update.setValidDays(dto.getValidDays());
        update.setUseRules(dto.getUseRules());
        applyFlashFields(update, dto);
        // 抢购已经开始后，库存、限购和时间窗被锁住（103007）；上下架、名称、价格、图不受限制
        if (current.getType() == ProductType.FLASH && flashLocked(current, update)) {
            throw new BizException(ProductErrorCode.FLASH_STARTED_LOCKED);
        }
        // soldCount 不在这里改：它只由订单的支付（+1）和退款（−1）维护
        productMapper.updateById(update);

        searchSyncPublisher.publishProductChanged(productId);
        return toProductVO(getRequiredProduct(productId), shopService.getRequiredShop(shopId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long productId, ProductStatus status) {
        getRequiredProduct(productId);
        Product update = new Product();
        update.setId(productId);
        update.setStatus(status);
        productMapper.updateById(update);
        searchSyncPublisher.publishProductChanged(productId);
    }

    // ---------- 供其他包调用 ----------

    @Override
    public Product getRequiredProduct(Long productId) {
        Product product = productId == null ? null : productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    @Override
    public Map<Long, Product> getProductsByIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    @Override
    public Product lockProduct(Long productId) {
        Product product = productId == null ? null : productMapper.selectByIdForUpdate(productId);
        if (product == null) {
            throw new BizException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    @Override
    public void changeSoldCount(Long productId, int delta) {
        if (productMapper.changeSoldCount(productId, delta) == 1) {
            searchSyncPublisher.publishProductChanged(productId);
        }
    }

    @Override
    public boolean decreaseFlashStock(Long productId) {
        if (productMapper.decreaseStock(productId) != 1) {
            return false;
        }
        searchSyncPublisher.publishProductChanged(productId);
        return true;
    }

    @Override
    public void increaseFlashStock(Long productId) {
        if (productMapper.increaseStock(productId) == 1) {
            searchSyncPublisher.publishProductChanged(productId);
        }
    }

    // ---------- 内部工具 ----------

    /**
     * 抢购商品的库存、限购、时间窗；NORMAL 一律存 NULL，不记库存。
     * 条件必填（stock / 开抢时间 / 结束时间）在这里校验成 103005、103006。
     */
    private void applyFlashFields(Product product, ProductSaveDTO dto) {
        if (dto.getType() != ProductType.FLASH) {
            product.setStock(null);
            product.setFlashStartTime(null);
            product.setFlashEndTime(null);
            product.setLimitPerUser(null);
            return;
        }
        if (dto.getStock() == null || dto.getFlashStartTime() == null || dto.getFlashEndTime() == null) {
            throw new BizException(ProductErrorCode.FLASH_PARAMS_REQUIRED);
        }
        if (!dto.getFlashEndTime().isAfter(dto.getFlashStartTime())) {
            throw new BizException(ProductErrorCode.FLASH_TIME_INVALID);
        }
        product.setStock(dto.getStock());
        product.setFlashStartTime(dto.getFlashStartTime());
        product.setFlashEndTime(dto.getFlashEndTime());
        product.setLimitPerUser(dto.getLimitPerUser() == null ? 1 : dto.getLimitPerUser());
    }

    /** 抢购已开始且库存 / 限购 / 时间窗有变化时返回 true。 */
    private boolean flashLocked(Product current, Product update) {
        boolean started = current.getFlashStartTime() != null
                && !LocalDateTime.now().isBefore(current.getFlashStartTime());
        if (!started) {
            return false;
        }
        return !Objects.equals(current.getStock(), update.getStock())
                || !Objects.equals(current.getLimitPerUser(), update.getLimitPerUser())
                || !Objects.equals(current.getFlashStartTime(), update.getFlashStartTime())
                || !Objects.equals(current.getFlashEndTime(), update.getFlashEndTime());
    }

    private Long parseId(String raw, String field) {
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, field + " 必须是数字");
        }
    }

    /** 一次批量查店名，避免列表接口 N+1；顺便去重，同一页里多个商品常属同一家店。 */
    private Map<Long, Shop> loadShops(List<Product> products) {
        return shopService.getShopsByIds(products.stream()
                .map(Product::getShopId)
                .distinct()
                .toList());
    }

    private List<ProductCardVO> toCardVOs(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        return toCardVOs(products, loadShops(products));
    }

    private List<ProductCardVO> toCardVOs(List<Product> products, Map<Long, Shop> shops) {
        List<ProductCardVO> cards = new ArrayList<>(products.size());
        for (Product product : products) {
            Shop shop = shops.get(product.getShopId());
            ProductCardVO vo = new ProductCardVO();
            vo.setId(String.valueOf(product.getId()));
            vo.setShopId(String.valueOf(product.getShopId()));
            vo.setShopName(shop == null ? null : shop.getName());
            vo.setName(product.getName());
            vo.setPrice(product.getPrice());
            vo.setType(product.getType());
            vo.setImageUrl(fileUrlResolver.toUrl(product.getImage()));
            vo.setSoldCount(product.getSoldCount());
            cards.add(vo);
        }
        return cards;
    }

    private ProductVO toProductVO(Product product, Shop shop) {
        ProductVO vo = new ProductVO();
        vo.setId(String.valueOf(product.getId()));
        vo.setShopId(String.valueOf(product.getShopId()));
        vo.setShopName(shop == null ? null : shop.getName());
        vo.setShopAddress(shop == null ? null : shop.getAddress());
        vo.setName(product.getName());
        vo.setContents(product.getContents() == null ? List.of() : product.getContents());
        vo.setPrice(product.getPrice());
        vo.setType(product.getType());
        vo.setImage(product.getImage());
        vo.setImageUrl(fileUrlResolver.toUrl(product.getImage()));
        vo.setStatus(product.getStatus());
        vo.setSoldCount(product.getSoldCount());
        vo.setValidDays(product.getValidDays());
        vo.setUseRules(product.getUseRules() == null ? List.of() : product.getUseRules());
        // 剩余库存、时间窗、限购只对 FLASH 有值，NORMAL 是 null
        vo.setRemainingStock(product.getStock());
        vo.setFlashStartTime(product.getFlashStartTime());
        vo.setFlashEndTime(product.getFlashEndTime());
        vo.setLimitPerUser(product.getLimitPerUser());
        vo.setCreateTime(product.getCreatedAt());
        vo.setUpdateTime(product.getUpdatedAt());
        return vo;
    }
}
