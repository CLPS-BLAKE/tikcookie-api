package com.dss.search.service.impl;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.dss.common.error.CommonErrorCode;
import com.dss.common.exception.BizException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.mq.SearchSyncMessage;
import com.dss.common.result.PageQuery;
import com.dss.common.result.PageResult;
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
import com.dss.search.service.SearchService;
import com.dss.shop.model.enums.ShopCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 搜索只读既有 ES 索引。MySQL → ES 由 Logstash 负责，Java 不参与索引写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final FileUrlResolver fileUrlResolver;

    /** 对齐部署索引默认的 index.max_result_window，不使用高成本深分页。 */
    private static final int MAX_RESULT_WINDOW = 10_000;

    @Override
    public PageResult<ProductHitVO> searchProducts(ProductSearchQuery query) {
        validatePage(query);
        String keyword = normalizedKeyword(query.getKeyword());
        BoolQuery.Builder filters = new BoolQuery.Builder();
        filters.filter(q -> q.term(t -> t.field("status").value(ProductStatus.ON_SHELF.name())));
        if (query.getCategory() != null) {
            filters.filter(q -> q.term(t -> t.field("shopCategory").value(query.getCategory().name())));
        }
        if (query.getType() != null) {
            filters.filter(q -> q.term(t -> t.field("type").value(query.getType().name())));
        }
        if (keyword != null) {
            filters.must(q -> q.multiMatch(m -> m.fields("name", "contentsText", "shopName").query(keyword)));
        }
        BoolQuery bool = filters.build();
        NativeQueryBuilder builder = baseQuery(query).withQuery(q -> q.bool(bool));
        SearchSort sort = SearchSort.fromCode(query.getSort());
        if (sort == SearchSort.DEFAULT && keyword != null) {
            builder.withSort(s -> s.score(score -> score.order(SortOrder.Desc)));
        }
        switch (sort) {
            case DEFAULT, SALES -> builder.withSort(s -> s.field(f -> f.field("soldCount").order(SortOrder.Desc)));
            case PRICE_ASC -> builder.withSort(s -> s.field(f -> f.field("price").order(SortOrder.Asc)));
            case PRICE_DESC -> builder.withSort(s -> s.field(f -> f.field("price").order(SortOrder.Desc)));
        }
        stableSort(builder);
        try {
            var hits = elasticsearchOperations.search(builder.build(), ProductDoc.class);
            var list = hits.getSearchHits().stream().map(hit -> toProductHit(hit.getContent())).toList();
            return PageResult.of(list, hits.getTotalHits(), query.getPage(), query.getSize());
        } catch (RuntimeException e) {
            log.error("商品搜索查询失败", e);
            throw new BizException(SearchErrorCode.SEARCH_UNAVAILABLE);
        }
    }

    @Override
    public PageResult<ShopHitVO> searchShops(ShopSearchQuery query) {
        validatePage(query);
        String keyword = normalizedKeyword(query.getKeyword());
        BoolQuery.Builder filters = new BoolQuery.Builder();
        if (query.getCategory() != null) {
            filters.filter(q -> q.term(t -> t.field("category").value(query.getCategory().name())));
        }
        if (keyword != null) {
            filters.must(q -> q.multiMatch(m -> m.fields("name", "address").query(keyword)));
        }
        BoolQuery bool = filters.build();
        NativeQueryBuilder builder = baseQuery(query).withQuery(q -> q.bool(bool));
        if (keyword == null) {
            builder.withSort(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)));
        } else {
            builder.withSort(s -> s.score(score -> score.order(SortOrder.Desc)));
        }
        stableSort(builder);
        try {
            var hits = elasticsearchOperations.search(builder.build(), ShopDoc.class);
            var list = hits.getSearchHits().stream().map(hit -> toShopHit(hit.getContent())).toList();
            return PageResult.of(list, hits.getTotalHits(), query.getPage(), query.getSize());
        } catch (RuntimeException e) {
            log.error("店铺搜索查询失败", e);
            throw new BizException(SearchErrorCode.SEARCH_UNAVAILABLE);
        }
    }

    private NativeQueryBuilder baseQuery(PageQuery query) {
        return NativeQuery.builder().withPageable(PageRequest.of(query.getPage() - 1, query.getSize()))
                .withTrackTotalHits(true);
    }

    private void stableSort(NativeQueryBuilder builder) {
        // 不按 ES _id 排序；文档的 id 字段是支持 doc_values 的 keyword。
        builder.withSort(s -> s.field(f -> f.field("id").order(SortOrder.Asc)));
    }

    private void validatePage(PageQuery query) {
        if (query.getPage() < 1 || query.getSize() < 1 || query.getSize() > 50) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "page 至少为 1，size 必须在 1 到 50 之间");
        }
        if ((long) query.getPage() * query.getSize() > MAX_RESULT_WINDOW) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "搜索仅支持前 10000 条结果，请缩小搜索范围");
        }
    }

    private String normalizedKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.strip() : null;
    }

    private ProductHitVO toProductHit(ProductDoc doc) {
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

    private ShopHitVO toShopHit(ShopDoc doc) {
        ShopHitVO vo = new ShopHitVO();
        vo.setId(doc.getId());
        vo.setName(doc.getName());
        vo.setAddress(doc.getAddress());
        vo.setCategory(doc.getCategory() == null ? null : ShopCategory.valueOf(doc.getCategory()));
        vo.setCoverUrl(fileUrlResolver.toUrl(doc.getCover()));
        vo.setBusinessHours(doc.getBusinessHours());
        return vo;
    }

    @Override
    public void sync(SearchSyncMessage message) {
        throw new UnsupportedOperationException("搜索索引由 Logstash 同步，禁止 Java/MQ 写入");
    }

    @Override
    public void rebuildAll() {
        throw new UnsupportedOperationException("搜索索引由 Logstash 维护，禁止 Java 删除重建；请设置 DSS_SEARCH_REBUILD_ON_STARTUP=false");
    }
}
