package com.dss.search.service.impl;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.dss.common.config.DssProperties;
import com.dss.common.exception.BizException;
import com.dss.common.file.FileUrlResolver;
import com.dss.product.model.enums.ProductType;
import com.dss.search.model.dto.ProductSearchQuery;
import com.dss.search.model.dto.ShopSearchQuery;
import com.dss.search.model.entity.ProductDoc;
import com.dss.search.model.entity.ShopDoc;
import com.dss.shop.model.enums.ShopCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {
    @Mock private ElasticsearchOperations operations;
    @Spy private FileUrlResolver resolver = resolver();
    @InjectMocks private SearchServiceImpl service;

    @Test
    void productsUseAllFiltersAndRelevantFieldsWithExactPagination() {
        products(List.of(), 10001);
        ProductSearchQuery request = new ProductSearchQuery();
        request.setKeyword("  牛肉面  ");
        request.setCategory(ShopCategory.FOOD);
        request.setType(ProductType.NORMAL);
        request.setPage(3);
        request.setSize(20);

        var result = service.searchProducts(request);
        NativeQuery query = productQuery();
        var bool = query.getQuery().bool();
        assertThat(bool.filter()).hasSize(3);
        assertThat(bool.filter().get(0).term().field()).isEqualTo("status");
        assertThat(bool.filter().get(0).term().value().stringValue()).isEqualTo("ON_SHELF");
        assertThat(bool.filter().get(1).term().field()).isEqualTo("shopCategory");
        assertThat(bool.filter().get(1).term().value().stringValue()).isEqualTo("FOOD");
        assertThat(bool.filter().get(2).term().field()).isEqualTo("type");
        assertThat(bool.filter().get(2).term().value().stringValue()).isEqualTo("NORMAL");
        assertThat(bool.must()).hasSize(1);
        assertThat(bool.must().get(0).multiMatch().query()).isEqualTo("牛肉面");
        assertThat(bool.must().get(0).multiMatch().fields()).containsExactly("name", "contentsText", "shopName");
        assertThat(query.getSortOptions().get(0).score().order()).isEqualTo(SortOrder.Desc);
        assertThat(query.getSortOptions().get(1).field().field()).isEqualTo("soldCount");
        assertThat(query.getSortOptions().get(1).field().order()).isEqualTo(SortOrder.Desc);
        assertStableIdSort(query);
        assertThat(query.getTrackTotalHits()).isTrue();
        assertThat(query.getPageable().getOffset()).isEqualTo(40);
        assertThat(query.getPageable().getPageSize()).isEqualTo(20);
        assertThat(result.getList()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(10001);
        assertThat(result.getPage()).isEqualTo(3);
        assertThat(result.getSize()).isEqualTo(20);
    }

    @ParameterizedTest
    @CsvSource({"default,soldCount,Desc", "sales,soldCount,Desc", "price_asc,price,Asc", "price_desc,price,Desc"})
    void productsWithoutKeywordUseRequestedSort(String sort, String field, SortOrder order) {
        products(List.of(), 0);
        ProductSearchQuery request = new ProductSearchQuery();
        request.setKeyword(" \t ");
        request.setSort(sort);
        service.searchProducts(request);
        NativeQuery query = productQuery();
        assertThat(query.getQuery().bool().must()).isEmpty();
        assertThat(query.getQuery().bool().filter()).hasSize(1);
        assertThat(query.getSortOptions()).hasSize(2);
        assertThat(query.getSortOptions().get(0).field().field()).isEqualTo(field);
        assertThat(query.getSortOptions().get(0).field().order()).isEqualTo(order);
        assertStableIdSort(query);
    }

    @ParameterizedTest
    @CsvSource({"sales,soldCount,Desc", "price_asc,price,Asc", "price_desc,price,Desc"})
    void explicitSortOverridesScoreEvenWithKeyword(String sort, String field, SortOrder order) {
        products(List.of(), 0);
        ProductSearchQuery request = new ProductSearchQuery();
        request.setKeyword("面");
        request.setSort(sort);
        service.searchProducts(request);
        NativeQuery query = productQuery();
        assertThat(query.getQuery().bool().must()).hasSize(1);
        assertThat(query.getSortOptions()).hasSize(2);
        assertThat(query.getSortOptions().get(0).field().field()).isEqualTo(field);
        assertThat(query.getSortOptions().get(0).field().order()).isEqualTo(order);
    }

    @Test
    void mapsProductFieldsWithoutLosingIdPriceOrFlashTimes() {
        ProductDoc doc = new ProductDoc();
        doc.setId("9007199254740993");
        doc.setShopId("2");
        doc.setName("双人套餐");
        doc.setShopName("面馆");
        doc.setShopCategory("FOOD");
        doc.setType("FLASH");
        doc.setPrice(12345L);
        doc.setSoldCount(7);
        doc.setImage("img/test.png");
        doc.setFlashStartTime(LocalDateTime.of(2026, 10, 8, 12, 0));
        doc.setFlashEndTime(doc.getFlashStartTime().plusHours(1));
        products(List.of(doc), 1);
        var hit = service.searchProducts(new ProductSearchQuery()).getList().getFirst();
        assertThat(hit.getId()).isEqualTo(doc.getId());
        assertThat(hit.getShopId()).isEqualTo("2");
        assertThat(hit.getName()).isEqualTo("双人套餐");
        assertThat(hit.getShopName()).isEqualTo("面馆");
        assertThat(hit.getShopCategory()).isEqualTo(ShopCategory.FOOD);
        assertThat(hit.getType()).isEqualTo(ProductType.FLASH);
        assertThat(hit.getPrice()).isEqualTo(12345);
        assertThat(hit.getSoldCount()).isEqualTo(7);
        assertThat(hit.getImageUrl()).isEqualTo("https://images.example.com/img/test.png");
        assertThat(hit.getFlashStartTime()).isEqualTo(doc.getFlashStartTime());
        assertThat(hit.getFlashEndTime()).isEqualTo(doc.getFlashEndTime());
    }

    @Test
    void mapsMissingProductImageAndOptionalFlashFieldsToNull() {
        ProductDoc doc = new ProductDoc();
        doc.setId("1");
        doc.setType("NORMAL");
        doc.setShopCategory("FOOD");
        products(List.of(doc), 1);
        var hit = service.searchProducts(new ProductSearchQuery()).getList().getFirst();
        assertThat(hit.getImageUrl()).isNull();
        assertThat(hit.getFlashStartTime()).isNull();
        assertThat(hit.getFlashEndTime()).isNull();
    }

    @Test
    void shopsBrowseByCreationTimeAndCategoryWithPagination() {
        shops(List.of(), 8);
        ShopSearchQuery request = new ShopSearchQuery();
        request.setCategory(ShopCategory.FOOD);
        request.setPage(2);
        request.setSize(5);
        var result = service.searchShops(request);
        NativeQuery query = shopQuery();
        assertThat(query.getQuery().bool().filter()).hasSize(1);
        assertThat(query.getQuery().bool().filter().get(0).term().field()).isEqualTo("category");
        assertThat(query.getQuery().bool().filter().get(0).term().value().stringValue()).isEqualTo("FOOD");
        assertThat(query.getQuery().bool().must()).isEmpty();
        assertThat(query.getSortOptions().get(0).field().field()).isEqualTo("createdAt");
        assertThat(query.getSortOptions().get(0).field().order()).isEqualTo(SortOrder.Desc);
        assertStableIdSort(query);
        assertThat(query.getTrackTotalHits()).isTrue();
        assertThat(query.getPageable().getOffset()).isEqualTo(5);
        assertThat(result.getList()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(8);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
    }

    @Test
    void shopsSearchNameAndAddressByRelevance() {
        shops(List.of(), 0);
        ShopSearchQuery request = new ShopSearchQuery();
        request.setKeyword("  人民路 ");
        service.searchShops(request);
        NativeQuery query = shopQuery();
        var must = query.getQuery().bool().must();
        assertThat(must).hasSize(1);
        assertThat(must.getFirst().multiMatch().fields()).containsExactly("name", "address");
        assertThat(must.getFirst().multiMatch().query()).isEqualTo("人民路");
        assertThat(query.getSortOptions().getFirst().score().order()).isEqualTo(SortOrder.Desc);
        assertStableIdSort(query);
    }

    @Test
    void unfilteredShopBrowseAndWhitespaceKeywordAreSupported() {
        shops(List.of(), 0);
        ShopSearchQuery request = new ShopSearchQuery();
        request.setKeyword(" \t ");
        service.searchShops(request);
        NativeQuery query = shopQuery();
        assertThat(query.getQuery().bool().filter()).isEmpty();
        assertThat(query.getQuery().bool().must()).isEmpty();
        assertThat(query.getSortOptions().getFirst().field().field()).isEqualTo("createdAt");
    }

    @Test
    void mapsShopFieldsAndNullableCover() {
        ShopDoc doc = new ShopDoc();
        doc.setId("9007199254740993");
        doc.setName("面馆");
        doc.setAddress("人民路1号");
        doc.setCategory("FOOD");
        doc.setCover("img/shop.png");
        doc.setBusinessHours("09:00-21:00");
        ShopDoc noCover = new ShopDoc();
        noCover.setCategory("FOOD");
        shops(List.of(doc, noCover), 2);
        var result = service.searchShops(new ShopSearchQuery());
        var hit = result.getList().getFirst();
        assertThat(hit.getId()).isEqualTo(doc.getId());
        assertThat(hit.getName()).isEqualTo(doc.getName());
        assertThat(hit.getAddress()).isEqualTo(doc.getAddress());
        assertThat(hit.getCategory()).isEqualTo(ShopCategory.FOOD);
        assertThat(hit.getCoverUrl()).isEqualTo("https://images.example.com/img/shop.png");
        assertThat(hit.getBusinessHours()).isEqualTo("09:00-21:00");
        assertThat(result.getList().get(1).getCoverUrl()).isNull();
        assertThat(result.getList().get(1).getBusinessHours()).isNull();
    }

    @Test
    void productsEsFailureBecomes107001WithoutInternalMessage() {
        when(operations.search(any(Query.class), eq(ProductDoc.class)))
                .thenThrow(new IllegalStateException("credential/private-host"));
        assertThatThrownBy(() -> service.searchProducts(new ProductSearchQuery()))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getErrorCode().getCode()).isEqualTo(107001);
                    assertThat(e.getMessage()).isEqualTo("搜索服务暂不可用");
                });
    }

    @Test
    void shopsEsFailureBecomes107001() {
        when(operations.search(any(Query.class), eq(ShopDoc.class)))
                .thenThrow(new IllegalStateException("connection refused"));
        assertThatThrownBy(() -> service.searchShops(new ShopSearchQuery()))
                .isInstanceOfSatisfying(BizException.class, e -> assertThat(e.getErrorCode().getCode()).isEqualTo(107001));
    }

    @ParameterizedTest
    @CsvSource({"201,50", "2147483647,50", "0,10", "1,0", "1,51"})
    void rejectsInvalidOrTooDeepPaginationBeforeEs(int page, int size) {
        ProductSearchQuery products = new ProductSearchQuery();
        products.setPage(page);
        products.setSize(size);
        ShopSearchQuery shops = new ShopSearchQuery();
        shops.setPage(page);
        shops.setSize(size);
        assertThatThrownBy(() -> service.searchProducts(products)).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(40000));
        assertThatThrownBy(() -> service.searchShops(shops)).isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(40000));
        verifyNoInteractions(operations);
    }

    @Test
    void lastSupportedResultWindowCanBeRequested() {
        products(List.of(), 0);
        ProductSearchQuery request = new ProductSearchQuery();
        request.setPage(200);
        request.setSize(50);
        service.searchProducts(request);
        assertThat(productQuery().getPageable().getOffset()).isEqualTo(9950);
    }

    private NativeQuery productQuery() {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(operations).search(captor.capture(), eq(ProductDoc.class));
        return (NativeQuery) captor.getValue();
    }

    private NativeQuery shopQuery() {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(operations).search(captor.capture(), eq(ShopDoc.class));
        return (NativeQuery) captor.getValue();
    }

    private void assertStableIdSort(NativeQuery query) {
        var last = query.getSortOptions().getLast().field();
        assertThat(last.field()).isEqualTo("id");
        assertThat(last.order()).isEqualTo(SortOrder.Asc);
    }

    private void products(List<ProductDoc> docs, long total) {
        SearchHits<ProductDoc> response = hits(docs, total);
        when(operations.search(any(Query.class), eq(ProductDoc.class))).thenReturn(response);
    }

    private void shops(List<ShopDoc> docs, long total) {
        SearchHits<ShopDoc> response = hits(docs, total);
        when(operations.search(any(Query.class), eq(ShopDoc.class))).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private static <T> SearchHits<T> hits(List<T> docs, long total) {
        SearchHits<T> hits = mock(SearchHits.class);
        List<SearchHit<T>> results = docs.stream().map(doc -> {
            SearchHit<T> hit = mock(SearchHit.class);
            when(hit.getContent()).thenReturn(doc);
            return hit;
        }).toList();
        when(hits.getSearchHits()).thenReturn(results);
        when(hits.getTotalHits()).thenReturn(total);
        return hits;
    }

    private static FileUrlResolver resolver() {
        DssProperties properties = new DssProperties();
        properties.getFile().setBaseUrl("https://images.example.com");
        return new FileUrlResolver(properties);
    }
}
