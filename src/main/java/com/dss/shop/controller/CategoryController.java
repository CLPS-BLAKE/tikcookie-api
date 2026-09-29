package com.dss.shop.controller;

import com.dss.common.result.Result;
import com.dss.shop.model.vo.CategoryVO;
import com.dss.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类（代码里的枚举，不入库）。接口文档 5.3.1。
 */
@Tag(name = "分类与店铺")
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final ShopService shopService;

    @Operation(summary = "分类列表（公开）")
    @GetMapping("/api/v1/categories")
    public Result<List<CategoryVO>> list() {
        return Result.ok(shopService.listCategories());
    }
}
