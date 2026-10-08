package com.dss.search;

import com.dss.common.config.DssProperties;
import com.dss.common.config.JacksonConfig;
import com.dss.common.exception.GlobalExceptionHandler;
import com.dss.common.file.FileUrlResolver;
import com.dss.search.controller.SearchController;
import com.dss.search.model.dto.ProductSearchQuery;
import com.dss.search.model.dto.ShopSearchQuery;
import com.dss.search.model.entity.ProductDoc;
import com.dss.search.model.entity.ShopDoc;
import com.dss.search.service.impl.SearchServiceImpl;
import com.dss.shop.model.enums.ShopCategory;
import com.dss.product.model.enums.ProductType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 只读真实 ES + 实际 SearchService/Controller。显式开启后，连接失败是失败而非跳过。 */
@EnabledIfEnvironmentVariable(named = "DSS_ES_LIVE_TEST", matches = "(?i)true")
class SearchApiEsLiveTest {
    private static ConfigurableApplicationContext context;
    private static SearchServiceImpl service;
    private static MockMvc mvc;
    private static List<ProductDoc> onShelf;
    private static List<ShopDoc> shops;
    private static FileUrlResolver resolver;

    @BeforeAll
    static void setUp() throws Exception {
        Properties env = new Properties();
        Path path = Path.of(".env");
        if (Files.isReadable(path)) {
            try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                env.load(in);
            }
        }
        // 环境变量优先；只读取 ES 和图片配置，不读取/输出数据库、RabbitMQ 或 OSS 密钥。
        String uris = value(env, "DSS_ES_URIS", "");
        assertThat(uris).as("显式开启后必须提供 DSS_ES_URIS").isNotBlank();
        // 独立上下文使用相同 Boot 配置；不扫描应用，不启动任何其它中间件或同步入口。
        var actual = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(actual,
                "spring.elasticsearch.uris=" + uris,
                "spring.elasticsearch.username=" + value(env, "DSS_ES_USERNAME", ""),
                "spring.elasticsearch.password=" + value(env, "DSS_ES_PASSWORD", ""),
                "spring.elasticsearch.connection-timeout=5s", "spring.elasticsearch.socket-timeout=15s");
        actual.register(ElasticsearchRestClientAutoConfiguration.class, ElasticsearchClientAutoConfiguration.class,
                ElasticsearchDataAutoConfiguration.class);
        context = actual;
        actual.refresh();
        ElasticsearchOperations operations = context.getBean(ElasticsearchOperations.class);
        DssProperties properties = new DssProperties();
        properties.getFile().setBaseUrl(value(env, "DSS_FILE_BASE_URL", ""));
        properties.getFile().getOss().setEndpoint(value(env, "DSS_OSS_ENDPOINT", ""));
        properties.getFile().getOss().setBucket(value(env, "DSS_OSS_BUCKET", ""));
        resolver = new FileUrlResolver(properties);
        service = new SearchServiceImpl(operations, resolver);
        var builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().dssJacksonCustomizer().customize(builder);
        mvc = MockMvcBuilders.standaloneSetup(new SearchController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(builder.build())).build();
        NativeQuery snapshot = NativeQuery.builder().withQuery(q -> q.matchAll(m -> m))
                .withPageable(PageRequest.of(0, 1000)).withTrackTotalHits(true).build();
        var productHits = operations.search(snapshot, ProductDoc.class);
        var shopHits = operations.search(snapshot, ShopDoc.class);
        assertThat(productHits.getTotalHits()).as("验收数据需在 1000 条只读抽样窗口内").isLessThanOrEqualTo(1000);
        assertThat(shopHits.getTotalHits()).isLessThanOrEqualTo(1000);
        onShelf = productHits.getSearchHits().stream().map(h -> h.getContent())
                .filter(d -> "ON_SHELF".equals(d.getStatus())).toList();
        shops = shopHits.getSearchHits().stream().map(h -> h.getContent()).toList();
        assertThat(onShelf).as("验收索引需要至少一个上架商品").isNotEmpty();
        assertThat(shops).as("验收索引需要至少一个店铺").isNotEmpty();
        System.out.printf("[ES READ-ONLY] products=%d, onShelf=%d, shops=%d%n",
                productHits.getTotalHits(), onShelf.size(), shops.size());
        System.out.println("[ES READ-ONLY] productTypes=" + onShelf.stream().collect(
                java.util.stream.Collectors.groupingBy(ProductDoc::getType, java.util.stream.Collectors.counting())));
    }

    @AfterAll
    static void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "sales", "price_asc", "price_desc"})
    void productSortsReturnOnlyOnShelfAndAccurateTotal(String sort) {
        ProductSearchQuery query = new ProductSearchQuery();
        query.setSize(50);
        query.setSort(sort);
        var page = service.searchProducts(query);
        assertThat(page.getTotal()).isEqualTo(onShelf.size());
        assertThat(page.getList()).extracting(h -> h.getId()).isSubsetOf(onShelf.stream().map(ProductDoc::getId).toList());
        assertThat(page.getList()).hasSize(Math.min(50, onShelf.size()));
        if (sort.startsWith("price")) {
            var prices = page.getList().stream().map(h -> h.getPrice()).toList();
            assertThat(prices).isSortedAccordingTo(sort.equals("price_asc") ? Comparator.naturalOrder() : Comparator.reverseOrder());
        } else {
            assertThat(page.getList()).extracting(h -> h.getSoldCount()).isSortedAccordingTo(Comparator.reverseOrder());
        }
    }

    @Test
    void productCategoryTypeAndNameKeywordUseDeployedFields() {
        var sample = onShelf.getFirst();
        ProductSearchQuery query = new ProductSearchQuery();
        query.setSize(50);
        query.setCategory(ShopCategory.valueOf(sample.getShopCategory()));
        query.setType(ProductType.valueOf(sample.getType()));
        long expected = onShelf.stream().filter(d -> sample.getShopCategory().equals(d.getShopCategory())
                && sample.getType().equals(d.getType())).count();
        assertThat(service.searchProducts(query).getTotal()).isEqualTo(expected);
        query.setKeyword(sample.getName());
        var page = service.searchProducts(query);
        assertThat(page.getList()).extracting(h -> h.getId()).contains(sample.getId());
        assertThat(page.getList()).allSatisfy(h -> {
            assertThat(h.getShopCategory()).isEqualTo(query.getCategory());
            assertThat(h.getType()).isEqualTo(query.getType());
        });
    }

    @Test
    void productPaginationIsStableAndDoesNotDuplicateFirstPage() {
        ProductSearchQuery query = new ProductSearchQuery();
        query.setSize(1);
        var first = service.searchProducts(query);
        query.setPage(2);
        var second = service.searchProducts(query);
        assertThat(second.getTotal()).isEqualTo(first.getTotal());
        if (onShelf.size() > 1) {
            assertThat(second.getList()).hasSize(1);
            assertThat(second.getList().getFirst().getId()).isNotEqualTo(first.getList().getFirst().getId());
        } else {
            assertThat(second.getList()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"NORMAL", "FLASH"})
    void eachProductTypeFilterMatchesSnapshotIncludingAnEmptyType(String type) {
        ProductSearchQuery query = new ProductSearchQuery();
        query.setType(ProductType.valueOf(type));
        query.setSize(50);
        var page = service.searchProducts(query);
        assertThat(page.getTotal()).isEqualTo(onShelf.stream().filter(d -> type.equals(d.getType())).count());
        assertThat(page.getList()).allSatisfy(h -> assertThat(h.getType()).isEqualTo(query.getType()));
    }

    @Test
    void shopBrowseCategoryKeywordAndCoverWorkWithRealIndex() {
        ShopSearchQuery query = new ShopSearchQuery();
        query.setSize(50);
        var page = service.searchShops(query);
        assertThat(page.getTotal()).isEqualTo(shops.size());
        var expected = shops.stream().sorted(Comparator.comparing(ShopDoc::getCreatedAt).reversed()
                .thenComparing(ShopDoc::getId)).limit(50).map(ShopDoc::getId).toList();
        assertThat(page.getList()).extracting(h -> h.getId()).containsExactlyElementsOf(expected);
        var sample = shops.getFirst();
        query.setCategory(ShopCategory.valueOf(sample.getCategory()));
        assertThat(service.searchShops(query).getTotal()).isEqualTo(shops.stream()
                .filter(s -> sample.getCategory().equals(s.getCategory())).count());
        query.setKeyword(sample.getName());
        assertThat(service.searchShops(query).getList()).extracting(h -> h.getId()).contains(sample.getId());
        query.setKeyword(sample.getAddress());
        assertThat(service.searchShops(query).getList()).extracting(h -> h.getId()).contains(sample.getId());
        for (var hit : page.getList()) {
            var source = shops.stream().filter(s -> s.getId().equals(hit.getId())).findFirst().orElseThrow();
            assertThat(hit.getCoverUrl()).isEqualTo(resolver.toUrl(source.getCover()));
            assertThat(hit.getBusinessHours()).isEqualTo(source.getBusinessHours());
        }
    }

    @Test
    void actualControllersReturnSuccessStringsAndIntegerPrices() throws Exception {
        mvc.perform(get("/api/v1/search/products").param("size", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(onShelf.size()))
                .andExpect(jsonPath("$.data.list[0].id").isString())
                .andExpect(jsonPath("$.data.list[0].price").isNumber());
        mvc.perform(get("/api/v1/search/shops").param("size", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(shops.size()));
    }

    @Test
    void deepPaginationIs400AndNoResultIsSuccessfulEmptyPage() throws Exception {
        mvc.perform(get("/api/v1/search/products").param("page", "201").param("size", "50"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        mvc.perform(get("/api/v1/search/shops").param("page", "2147483647").param("size", "50"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        mvc.perform(get("/api/v1/search/products").param("keyword", "zznonexistentsearchprobe88776655"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.list").isEmpty());
    }

    private static String value(Properties env, String name, String fallback) {
        String system = System.getenv(name);
        return system != null ? system : env.getProperty(name, fallback);
    }
}
