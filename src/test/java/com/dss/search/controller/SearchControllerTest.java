package com.dss.search.controller;

import com.dss.common.exception.BizException;
import com.dss.common.config.JacksonConfig;
import com.dss.common.exception.GlobalExceptionHandler;
import com.dss.common.result.PageResult;
import com.dss.search.model.dto.ProductSearchQuery;
import com.dss.search.model.dto.ShopSearchQuery;
import com.dss.search.model.enums.SearchErrorCode;
import com.dss.search.model.vo.ProductHitVO;
import com.dss.search.model.vo.ShopHitVO;
import com.dss.search.service.SearchService;
import com.dss.shop.model.enums.ShopCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchControllerTest {
    private SearchService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(SearchService.class);
        var builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().dssJacksonCustomizer().customize(builder);
        mvc = MockMvcBuilders.standaloneSetup(new SearchController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(builder.build())).build();
    }

    @Test
    void productSearchUsesDefaultsAndReturnsPage() throws Exception {
        ProductHitVO hit = new ProductHitVO();
        hit.setId("9007199254740993");
        hit.setPrice(12345L);
        when(service.searchProducts(any())).thenReturn(PageResult.of(List.of(hit), 1, 1, 10));
        mvc.perform(get("/api/v1/search/products"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.data.list[0].price").value(12345))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(10));
        var captor = ArgumentCaptor.forClass(ProductSearchQuery.class);
        verify(service).searchProducts(captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo("default");
        assertThat(captor.getValue().getPage()).isEqualTo(1);
        assertThat(captor.getValue().getSize()).isEqualTo(10);
    }

    @Test
    void shopSearchBindsCategoryAndPagination() throws Exception {
        when(service.searchShops(any())).thenReturn(PageResult.<ShopHitVO>of(List.of(), 0, 2, 5));
        mvc.perform(get("/api/v1/search/shops").param("category", "FOOD").param("page", "2").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isEmpty()).andExpect(jsonPath("$.data.total").value(0));
        var captor = ArgumentCaptor.forClass(ShopSearchQuery.class);
        verify(service).searchShops(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(ShopCategory.FOOD);
        assertThat(captor.getValue().getPage()).isEqualTo(2);
        assertThat(captor.getValue().getSize()).isEqualTo(5);
    }

    @Test
    void flashTimesUseTheApplicationDateFormatAndAbsentTimesRemainNull() throws Exception {
        ProductHitVO flash = new ProductHitVO();
        flash.setFlashStartTime(LocalDateTime.of(2026, 10, 8, 12, 34, 56));
        flash.setFlashEndTime(flash.getFlashStartTime().plusHours(1));
        when(service.searchProducts(any())).thenReturn(PageResult.of(List.of(flash, new ProductHitVO()), 2, 1, 10));
        mvc.perform(get("/api/v1/search/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].flashStartTime").value("2026-10-08 12:34:56"))
                .andExpect(jsonPath("$.data.list[0].flashEndTime").value("2026-10-08 13:34:56"))
                .andExpect(jsonPath("$.data.list[1].flashStartTime").value(org.hamcrest.Matchers.nullValue()));
    }

    @ParameterizedTest
    @CsvSource({"products,page,0", "shops,page,0", "products,size,51", "shops,size,51", "products,size,0", "shops,size,0",
            "products,category,INVALID", "shops,category,INVALID", "products,type,INVALID", "products,sort,unknown",
            "products,page,abc", "shops,page,2147483648"})
    void rejectsInvalidParametersBeforeQuery(String endpoint, String name, String value) throws Exception {
        mvc.perform(get("/api/v1/search/" + endpoint).param(name, value))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"products", "shops"})
    void rejectsKeywordOver50Characters(String endpoint) throws Exception {
        mvc.perform(get("/api/v1/search/" + endpoint).param("keyword", "面".repeat(51)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(service);
    }

    @Test
    void bothApisExpose107001AsBusinessError() throws Exception {
        when(service.searchProducts(any())).thenThrow(new BizException(SearchErrorCode.SEARCH_UNAVAILABLE));
        when(service.searchShops(any())).thenThrow(new BizException(SearchErrorCode.SEARCH_UNAVAILABLE));
        for (String endpoint : List.of("products", "shops")) {
            mvc.perform(get("/api/v1/search/" + endpoint)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(107001))
                    .andExpect(jsonPath("$.msg").value("搜索服务暂不可用"));
        }
    }
}
