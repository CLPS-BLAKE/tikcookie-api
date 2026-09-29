package com.dss.shop.controller;

import com.dss.common.result.Result;
import com.dss.shop.model.vo.ShopVO;
import com.dss.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺详情。按分类浏览店铺走搜索接口（dss-search）。接口文档 5.3.2。
 */
@Tag(name = "分类与店铺")
@RestController
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @Operation(summary = "店铺详情（公开）")
    @GetMapping("/api/v1/shops/{shopId}")
    public Result<ShopVO> detail(@PathVariable Long shopId) {
        return Result.ok(shopService.getShop(shopId));
    }
}
