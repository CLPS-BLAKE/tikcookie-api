package com.dss.product.controller;

import com.dss.common.result.PageResult;
import com.dss.common.result.Result;
import com.dss.product.model.dto.ProductFeedQuery;
import com.dss.product.model.vo.FlashProductVO;
import com.dss.product.model.vo.ProductCardVO;
import com.dss.product.model.vo.ProductVO;
import com.dss.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品（全部公开）。接口文档 5.4。
 * "店铺的上架商品"的路径在 /shops 下，但数据属于商品，所以放在这里。
 */
@Tag(name = "商品")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "店铺的上架商品（公开）", description = "按创建时间倒序，不分页")
    @GetMapping("/shops/{shopId}/products")
    public Result<List<ProductCardVO>> listShopProducts(@PathVariable Long shopId) {
        return Result.ok(productService.listShopProducts(shopId));
    }

    @Operation(summary = "商品详情（公开）", description = "抢购商品带剩余库存（以 MySQL 为准）")
    @GetMapping("/products/{productId}")
    public Result<ProductVO> detail(@PathVariable Long productId) {
        return Result.ok(productService.getProduct(productId));
    }

    @Operation(summary = "首页商品流（公开）", description = "sort=latest（默认）按创建时间倒序，sort=sales 按已售数倒序")
    @GetMapping("/products")
    public Result<PageResult<ProductCardVO>> feed(@Valid @ParameterObject ProductFeedQuery query) {
        return Result.ok(productService.listFeed(query));
    }

    @Operation(summary = "抢购专区（公开）", description = "进行中和即将开始的抢购商品，按开抢时间升序")
    @GetMapping("/products/flash")
    public Result<List<FlashProductVO>> flash() {
        return Result.ok(productService.listFlash());
    }
}
