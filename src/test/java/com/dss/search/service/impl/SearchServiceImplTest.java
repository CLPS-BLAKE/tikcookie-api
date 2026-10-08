package com.dss.search.service.impl;

import com.dss.common.exception.BizException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.mq.DocType;
import com.dss.common.mq.SearchSyncMessage;
import com.dss.common.result.PageResult;
import com.dss.product.mapper.ProductMapper;
import com.dss.product.model.entity.ContentGroup;
import com.dss.product.model.entity.ContentItem;
import com.dss.product.model.entity.Product;
import com.dss.product.model.enums.ProductStatus;
import com.dss.product.model.enums.ProductType;
import com.dss.search.model.dto.ProductSearchQuery;
import com.dss.search.model.dto.ShopSearchQuery;
import com.dss.search.model.entity.ProductDoc;
import com.dss.search.model.entity.ShopDoc;
import com.dss.search.model.enums.SearchErrorCode;
import com.dss.search.model.vo.ProductHitVO;
import com.dss.search.model.vo.ShopHitVO;
import com.dss.search.repository.ProductDocRepository;
import com.dss.search.repository.ShopDocRepository;
import com.dss.shop.mapper.ShopMapper;
import com.dss.shop.model.entity.Shop;
import com.dss.shop.model.enums.ShopCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SearchServiceImpl 的单元测试：用 Mockito 隔离 ES / MySQL，验证文档组装（contentsText、冗余字段）、
 * VO 映射、搜索失败时的 107001，以及同步时"从 MySQL 读当前记录覆盖写 ES"。
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private ElasticsearchOperations operations;
    @Mock
    private ProductDocRepository productDocRepository;
    @Mock
    private ShopDocRepository shopDocRepository;
    @Mock
    private ShopMapper shopMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private FileUrlResolver fileUrlResolver;

    @InjectMocks
    private SearchServiceImpl service;

    @Test
    void searchProducts_mapsDocToVO() {
        ProductDoc doc = new ProductDoc();
        doc.setId("1");
        doc.setName("双人牛肉面套餐");
        doc.setShopId("9");
        doc.setShopName("老王牛肉面");
        doc.setShopCategory("FOOD");
        doc.setType("NORMAL");
        doc.setPrice(3990L);
        doc.setSoldCount(128);
        doc.setImage("img/p.jpg");

        SearchHits<ProductDoc> hits = searchHits(List.of(doc), 1L);
        when(operations.search(any(Query.class), eq(ProductDoc.class))).thenReturn(hits);
        when(fileUrlResolver.toUrl("img/p.jpg")).thenReturn("http://img.example.com/img/p.jpg");

        ProductSearchQuery query = new ProductSearchQuery();
        query.setKeyword("牛肉面");
        query.setPage(1);
        query.setSize(10);

        PageResult<ProductHitVO> result = service.searchProducts(query);

        assertThat(result.getTotal()).isEqualTo(1L);
        ProductHitVO vo = result.getList().get(0);
        assertThat(vo.getId()).isEqualTo("1");
        assertThat(vo.getName()).isEqualTo("双人牛肉面套餐");
        assertThat(vo.getShopCategory()).isEqualTo(ShopCategory.FOOD);
        assertThat(vo.getType()).isEqualTo(ProductType.NORMAL);
        assertThat(vo.getSoldCount()).isEqualTo(128);
        assertThat(vo.getImageUrl()).isEqualTo("http://img.example.com/img/p.jpg");
    }

    @Test
    void searchShops_mapsDocToVO() {
        ShopDoc doc = new ShopDoc();
        doc.setId("9");
        doc.setName("老王牛肉面");
        doc.setAddress("体育西路 100 号");
        doc.setCategory("FOOD");
        doc.setCover("img/cover.jpg");
        doc.setBusinessHours("10:00-22:00");

        SearchHits<ShopDoc> hits = searchHits(List.of(doc), 1L);
        when(operations.search(any(Query.class), eq(ShopDoc.class))).thenReturn(hits);
        when(fileUrlResolver.toUrl("img/cover.jpg")).thenReturn("http://img.example.com/img/cover.jpg");

        ShopSearchQuery query = new ShopSearchQuery();
        query.setPage(1);
        query.setSize(10);

        PageResult<ShopHitVO> result = service.searchShops(query);

        assertThat(result.getTotal()).isEqualTo(1L);
        ShopHitVO vo = result.getList().get(0);
        assertThat(vo.getId()).isEqualTo("9");
        assertThat(vo.getCategory()).isEqualTo(ShopCategory.FOOD);
        assertThat(vo.getCoverUrl()).isEqualTo("http://img.example.com/img/cover.jpg");
    }

    @Test
    void searchProducts_returns107001_whenEsFails() {
        when(operations.search(any(Query.class), eq(ProductDoc.class)))
                .thenThrow(new RuntimeException("es down"));

        ProductSearchQuery query = new ProductSearchQuery();
        query.setPage(1);
        query.setSize(10);

        assertThatThrownBy(() -> service.searchProducts(query))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(SearchErrorCode.SEARCH_UNAVAILABLE);
    }

    @Test
    void syncProduct_buildsDocFromMysql() {
        Product product = new Product();
        product.setId(10L);
        product.setShopId(5L);
        product.setName("双人套餐");
        product.setType(ProductType.NORMAL);
        product.setStatus(ProductStatus.ON_SHELF);
        product.setPrice(3990L);
        product.setSoldCount(10);
        product.setImage("img/p.jpg");
        product.setContents(List.of(
                new ContentGroup("主食", List.of(new ContentItem("招牌牛肉面", 2), new ContentItem("卤蛋", 1))),
                new ContentGroup("小食", List.of(new ContentItem("凉拌黄瓜", 1)))));

        Shop shop = new Shop();
        shop.setId(5L);
        shop.setName("老王牛肉面");
        shop.setCategory(ShopCategory.FOOD);

        when(productMapper.selectById(10L)).thenReturn(product);
        when(shopMapper.selectById(5L)).thenReturn(shop);

        service.sync(new SearchSyncMessage(DocType.PRODUCT, "10"));

        ArgumentCaptor<ProductDoc> captor = ArgumentCaptor.forClass(ProductDoc.class);
        verify(productDocRepository).save(captor.capture());
        ProductDoc doc = captor.getValue();
        assertThat(doc.getId()).isEqualTo("10");
        assertThat(doc.getShopId()).isEqualTo("5");
        assertThat(doc.getContentsText()).isEqualTo("招牌牛肉面 卤蛋 凉拌黄瓜");
        assertThat(doc.getShopName()).isEqualTo("老王牛肉面");
        assertThat(doc.getShopCategory()).isEqualTo("FOOD");
        assertThat(doc.getType()).isEqualTo("NORMAL");
        assertThat(doc.getStatus()).isEqualTo("ON_SHELF");
    }

    @Test
    void syncShop_refreshesShopAndItsProducts() {
        Shop shop = new Shop();
        shop.setId(5L);
        shop.setName("新店名");
        shop.setAddress("地址");
        shop.setCategory(ShopCategory.FOOD);
        shop.setImages(List.of("img/cover.jpg"));

        Product p1 = new Product();
        p1.setId(1L);
        p1.setShopId(5L);
        p1.setName("商品A");
        p1.setType(ProductType.NORMAL);
        p1.setStatus(ProductStatus.ON_SHELF);
        Product p2 = new Product();
        p2.setId(2L);
        p2.setShopId(5L);
        p2.setName("商品B");
        p2.setType(ProductType.FLASH);
        p2.setStatus(ProductStatus.ON_SHELF);

        when(shopMapper.selectById(5L)).thenReturn(shop);
        when(productMapper.selectList(any())).thenReturn(List.of(p1, p2));

        service.sync(new SearchSyncMessage(DocType.SHOP, "5"));

        verify(shopDocRepository).save(any(ShopDoc.class));
        // 店铺改名/改分类要刷新本店所有商品文档里的冗余字段
        verify(productDocRepository).saveAll(argThat(iterable -> iterable != null && count(iterable) == 2));
    }

    /** 数 Iterable 的元素个数（saveAll 的参数是 Iterable，没有 size()）。 */
    private static long count(Iterable<?> iterable) {
        long n = 0;
        for (Object ignored : iterable) {
            n++;
        }
        return n;
    }

    /** 用 Mockito 构造一个 SearchHits，只关心 getSearchHits / getTotalHits。 */
    @SuppressWarnings("unchecked")
    private <T> SearchHits<T> searchHits(List<T> contents, long total) {
        List<SearchHit<T>> hitList = contents.stream().map(c -> {
            SearchHit<T> hit = mock(SearchHit.class);
            when(hit.getContent()).thenReturn(c);
            return hit;
        }).toList();
        SearchHits<T> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(hitList);
        when(hits.getTotalHits()).thenReturn(total);
        return hits;
    }
}
