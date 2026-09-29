package com.dss.search.controller;

import com.dss.common.result.PageResult;
import com.dss.common.result.Result;
import com.dss.search.model.dto.ProductSearchQuery;
import com.dss.search.model.dto.ShopSearchQuery;
import com.dss.search.model.vo.ProductHitVO;
import com.dss.search.model.vo.ShopHitVO;
import com.dss.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索（全部公开）。按分类浏览店铺也走这里（关键词留空）。接口文档 5.5。
 */
@Tag(name = "搜索")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "商品搜索（公开）", description = "关键词匹配商品名、套餐内容、店铺名；sort = default / sales / price_asc / price_desc")
    @GetMapping("/products")
    public Result<PageResult<ProductHitVO>> searchProducts(@Valid @ParameterObject ProductSearchQuery query) {
        return Result.ok(searchService.searchProducts(query));
    }

    @Operation(summary = "店铺搜索（公开）", description = "关键词匹配店名和地址；关键词为空时就是按分类浏览")
    @GetMapping("/shops")
    public Result<PageResult<ShopHitVO>> searchShops(@Valid @ParameterObject ShopSearchQuery query) {
        return Result.ok(searchService.searchShops(query));
    }
}
