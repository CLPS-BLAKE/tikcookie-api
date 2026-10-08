package com.dss.search.service.impl;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dss.common.exception.BizException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.mq.SearchSyncMessage;
import com.dss.common.result.PageResult;
import com.dss.product.mapper.ProductMapper;
import com.dss.product.model.entity.ContentItem;
import com.dss.product.model.entity.Product;
import com.dss.product.model.enums.ProductStatus;
import com.dss.product.model.enums.ProductType;
import com.dss.search.model.dto.ProductSearchQuery;
import com.dss.search.model.dto.ShopSearchQuery;
import com.dss.search.model.entity.ProductDoc;
import com.dss.search.model.entity.ShopDoc;
import com.dss.search.model.enums.SearchErrorCode;
import com.dss.search.model.enums.SearchSort;
import com.dss.search.model.vo.ProductHitVO;
import com.dss.search.model.vo.ShopHitVO;
import com.dss.search.repository.ProductDocRepository;
import com.dss.search.repository.ShopDocRepository;
import com.dss.search.service.SearchService;
import com.dss.shop.mapper.ShopMapper;
import com.dss.shop.model.entity.Shop;
import com.dss.shop.model.enums.ShopCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 搜索与同步实现。规则见需求文档 4.3、中间件配置 5.2，索引字段见 {@link ProductDoc} / {@link ShopDoc}。
 * <p>
 * 搜索只查 ES（standard 分析器，不装 IK）；查询失败按接口文档返回 107001。同步（SearchSyncConsumer 调用）和
 * 全量重建都从 MySQL 读当前记录整条覆盖写 ES，所以消息重复 / 乱序都不会写错。
 * <p>
 * 商品详情、剩余库存、抢购时间窗始终以 MySQL 为准：ES 里的 soldCount / flash 时间只用于搜索排序和展示过滤，
 * 不作为下单依据（中间件配置 5.2）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    /** 全量重建时每批从 MySQL 读的商品条数。 */
    private static final int REBUILD_BATCH_SIZE = 500;

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductDocRepository productDocRepository;
    private final ShopDocRepository shopDocRepository;
    private final ShopMapper shopMapper;
    private final ProductMapper productMapper;
    private final FileUrlResolver fileUrlResolver;

    // ---------- 搜索 ----------

    @Override
    public PageResult<ProductHitVO> searchProducts(ProductSearchQuery query) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(buildProductQuery(query))
                .withSort(productSort(query))
                .withPageable(PageRequest.of(query.getPage() - 1, query.getSize()))
                .withTrackScores(StringUtils.hasText(query.getKeyword()))
                .build();
        try {
            SearchHits<ProductDoc> hits = elasticsearchOperations.search(nativeQuery, ProductDoc.class);
            List<ProductHitVO> list = hits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .map(this::toProductHitVO)
                    .toList();
            return PageResult.of(list, hits.getTotalHits(), query.getPage(), query.getSize());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("商品搜索失败：query={}", query, e);
            throw new BizException(SearchErrorCode.SEARCH_UNAVAILABLE);
        }
    }

    @Override
    public PageResult<ShopHitVO> searchShops(ShopSearchQuery query) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(buildShopQuery(query))
                .withSort(shopSort(query))
                .withPageable(PageRequest.of(query.getPage() - 1, query.getSize()))
                .withTrackScores(StringUtils.hasText(query.getKeyword()))
                .build();
        try {
            SearchHits<ShopDoc> hits = elasticsearchOperations.search(nativeQuery, ShopDoc.class);
            List<ShopHitVO> list = hits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .map(this::toShopHitVO)
                    .toList();
            return PageResult.of(list, hits.getTotalHits(), query.getPage(), query.getSize());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("店铺搜索失败：query={}", query, e);
            throw new BizException(SearchErrorCode.SEARCH_UNAVAILABLE);
        }
    }

    // ---------- 同步（SearchSyncConsumer 调用） ----------

    @Override
    public void sync(SearchSyncMessage message) {
        if (message == null || message.getType() == null) {
            log.warn("搜索同步消息为空或缺少类型，忽略");
            return;
        }
        Long id = parseId(message.getId());
        if (id == null) {
            log.warn("搜索同步消息缺少合法 ID，忽略：{}", message);
            return;
        }
        switch (message.getType()) {
            case SHOP -> syncShop(id);
            case PRODUCT -> syncProduct(id);
        }
        // 写 ES 失败这里会向上抛，交给 SearchSyncConsumer 的重试/死信（中间件配置 5.1）；
        // 记录在 MySQL 里不存在则跳过，避免把永久性缺失的消息反复重试。
    }

    private void syncShop(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            log.warn("店铺 {} 不存在，跳过搜索同步", shopId);
            return;
        }
        shopDocRepository.save(toShopDoc(shop));
        // 店名 / 分类冗余在商品文档里：店铺改名或改分类时，刷新本店所有商品文档（需求文档 4.3）
        List<Product> products = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getShopId, shopId));
        productDocRepository.saveAll(products.stream().map(product -> toProductDoc(product, shop)).toList());
    }

    private void syncProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.warn("商品 {} 不存在，跳过搜索同步", productId);
            return;
        }
        Shop shop = shopMapper.selectById(product.getShopId());
        // 下架不删文档，只更新 status（中间件配置 5.2）
        productDocRepository.save(toProductDoc(product, shop));
    }

    // ---------- 全量重建（SearchIndexRebuildRunner 调用） ----------

    @Override
    public void rebuildAll() {
        // 先删掉两个索引，再按实体注解重建 mapping（中间件配置 5.2）
        IndexOperations shopIndex = elasticsearchOperations.indexOps(ShopDoc.class);
        if (shopIndex.exists()) {
            shopIndex.delete();
        }
        shopIndex.createWithMapping();

        IndexOperations productIndex = elasticsearchOperations.indexOps(ProductDoc.class);
        if (productIndex.exists()) {
            productIndex.delete();
        }
        productIndex.createWithMapping();

        List<Shop> shops = shopMapper.selectList(null);
        shopDocRepository.saveAll(shops.stream().map(this::toShopDoc).toList());

        int pageNum = 1;
        while (true) {
            Page<Product> page = productMapper.selectPage(Page.of(pageNum, REBUILD_BATCH_SIZE), null);
            List<Product> records = page.getRecords();
            if (records.isEmpty()) {
                break;
            }
            Map<Long, Shop> shopById = shopMapper.selectBatchIds(records.stream()
                            .map(Product::getShopId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList())
                    .stream()
                    .collect(Collectors.toMap(Shop::getId, Function.identity()));
            productDocRepository.saveAll(records.stream()
                    .map(product -> toProductDoc(product, shopById.get(product.getShopId())))
                    .toList());
            if (records.size() < REBUILD_BATCH_SIZE) {
                break;
            }
            pageNum++;
        }
    }

    // ---------- 查询与排序构造 ----------

    /** 商品搜索条件：只搜上架，分类 / 类型做过滤，关键词多字段匹配。 */
    private Query buildProductQuery(ProductSearchQuery query) {
        return Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("status").value(ProductStatus.ON_SHELF.name())));
            if (query.getCategory() != null) {
                b.filter(f -> f.term(t -> t.field("shopCategory").value(query.getCategory().name())));
            }
            if (query.getType() != null) {
                b.filter(f -> f.term(t -> t.field("type").value(query.getType().name())));
            }
            if (StringUtils.hasText(query.getKeyword())) {
                b.must(m -> m.multiMatch(mm -> mm
                        .fields("name", "contentsText", "shopName")
                        .query(query.getKeyword().trim())));
            }
            return b;
        }));
    }

    /** 店铺搜索条件：分类过滤，关键词匹配店名和地址。 */
    private Query buildShopQuery(ShopSearchQuery query) {
        return Query.of(q -> q.bool(b -> {
            if (query.getCategory() != null) {
                b.filter(f -> f.term(t -> t.field("category").value(query.getCategory().name())));
            }
            if (StringUtils.hasText(query.getKeyword())) {
                b.must(m -> m.multiMatch(mm -> mm
                        .fields("name", "address")
                        .query(query.getKeyword().trim())));
            }
            return b;
        }));
    }

    /** 商品排序：default 综合（先相关度再已售数，无关键词时就是已售数）、sales 销量、price_asc / price_desc 价格。 */
    private List<SortOptions> productSort(ProductSearchQuery query) {
        SearchSort sort = SearchSort.fromCode(query.getSort());
        boolean hasKeyword = StringUtils.hasText(query.getKeyword());
        return switch (sort) {
            case SALES -> List.of(fieldSort("soldCount", SortOrder.Desc));
            case PRICE_ASC -> List.of(fieldSort("price", SortOrder.Asc));
            case PRICE_DESC -> List.of(fieldSort("price", SortOrder.Desc));
            case DEFAULT -> {
                if (!hasKeyword) {
                    yield List.of(fieldSort("soldCount", SortOrder.Desc));
                }
                List<SortOptions> sorts = new ArrayList<>(2);
                sorts.add(scoreSort(SortOrder.Desc));
                sorts.add(fieldSort("soldCount", SortOrder.Desc));
                yield sorts;
            }
        };
    }

    /** 店铺排序：无关键词按创建时间倒序（即按分类浏览），有关键词按相关度。 */
    private List<SortOptions> shopSort(ShopSearchQuery query) {
        if (StringUtils.hasText(query.getKeyword())) {
            return List.of(scoreSort(SortOrder.Desc));
        }
        return List.of(fieldSort("createdAt", SortOrder.Desc));
    }

    private SortOptions fieldSort(String field, SortOrder order) {
        return SortOptions.of(o -> o.field(f -> f.field(field).order(order)));
    }

    private SortOptions scoreSort(SortOrder order) {
        return SortOptions.of(o -> o.score(sc -> sc.order(order)));
    }

    // ---------- MySQL 记录 → ES 文档 ----------

    private ShopDoc toShopDoc(Shop shop) {
        ShopDoc doc = new ShopDoc();
        doc.setId(String.valueOf(shop.getId()));
        doc.setName(shop.getName());
        doc.setAddress(shop.getAddress());
        doc.setCategory(shop.getCategory() == null ? null : shop.getCategory().name());
        doc.setCover(shop.getImages() == null || shop.getImages().isEmpty() ? null : shop.getImages().get(0));
        doc.setBusinessHours(shop.getBusinessHours());
        doc.setCreatedAt(shop.getCreatedAt());
        return doc;
    }

    private ProductDoc toProductDoc(Product product, Shop shop) {
        ProductDoc doc = new ProductDoc();
        doc.setId(String.valueOf(product.getId()));
        doc.setShopId(String.valueOf(product.getShopId()));
        doc.setName(product.getName());
        doc.setContentsText(extractContentsText(product));
        doc.setShopName(shop == null ? null : shop.getName());
        doc.setShopCategory(shop == null || shop.getCategory() == null ? null : shop.getCategory().name());
        doc.setType(product.getType() == null ? null : product.getType().name());
        doc.setStatus(product.getStatus() == null ? null : product.getStatus().name());
        doc.setPrice(product.getPrice());
        doc.setSoldCount(product.getSoldCount());
        doc.setImage(product.getImage());
        doc.setFlashStartTime(product.getFlashStartTime());
        doc.setFlashEndTime(product.getFlashEndTime());
        return doc;
    }

    /** 套餐内容里所有项目名，用空格拼成一段文本，供关键词搜索。 */
    private String extractContentsText(Product product) {
        if (product.getContents() == null || product.getContents().isEmpty()) {
            return "";
        }
        return product.getContents().stream()
                .filter(Objects::nonNull)
                .flatMap(group -> (group.getItems() == null ? List.<ContentItem>of() : group.getItems()).stream())
                .filter(Objects::nonNull)
                .map(ContentItem::getName)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
    }

    // ---------- ES 文档 → 响应 VO ----------

    private ProductHitVO toProductHitVO(ProductDoc doc) {
        ProductHitVO vo = new ProductHitVO();
        vo.setId(doc.getId());
        vo.setName(doc.getName());
        vo.setShopId(doc.getShopId());
        vo.setShopName(doc.getShopName());
        vo.setShopCategory(doc.getShopCategory() == null ? null : ShopCategory.valueOf(doc.getShopCategory()));
        vo.setType(doc.getType() == null ? null : ProductType.valueOf(doc.getType()));
        vo.setPrice(doc.getPrice());
        vo.setSoldCount(doc.getSoldCount());
        vo.setImageUrl(fileUrlResolver.toUrl(doc.getImage()));
        vo.setFlashStartTime(doc.getFlashStartTime());
        vo.setFlashEndTime(doc.getFlashEndTime());
        return vo;
    }

    private ShopHitVO toShopHitVO(ShopDoc doc) {
        ShopHitVO vo = new ShopHitVO();
        vo.setId(doc.getId());
        vo.setName(doc.getName());
        vo.setAddress(doc.getAddress());
        vo.setCategory(doc.getCategory() == null ? null : ShopCategory.valueOf(doc.getCategory()));
        vo.setCoverUrl(fileUrlResolver.toUrl(doc.getCover()));
        vo.setBusinessHours(doc.getBusinessHours());
        return vo;
    }

    private Long parseId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
