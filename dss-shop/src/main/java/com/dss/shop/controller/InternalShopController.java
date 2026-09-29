package com.dss.shop.controller;

import com.dss.common.result.Result;
import com.dss.shop.model.dto.ShopSaveDTO;
import com.dss.shop.model.vo.ShopVO;
import com.dss.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部：录入店铺（请求头 X-Internal-Key）。接口文档 5.8.1–5.8.2。
 */
@Tag(name = "内部：店铺")
@RestController
@RequestMapping("/api/v1/internal/shops")
@RequiredArgsConstructor
public class InternalShopController {

    private final ShopService shopService;

    @Operation(summary = "新建店铺", description = "成功后发 shop.upsert 搜索同步事件")
    @PostMapping
    public Result<ShopVO> create(@Valid @RequestBody ShopSaveDTO dto) {
        return Result.ok(shopService.createShop(dto));
    }

    @Operation(summary = "修改店铺（整体覆盖）", description = "成功后发 shop.upsert 搜索同步事件")
    @PutMapping("/{shopId}")
    public Result<ShopVO> update(@PathVariable Long shopId, @Valid @RequestBody ShopSaveDTO dto) {
        return Result.ok(shopService.updateShop(shopId, dto));
    }
}
