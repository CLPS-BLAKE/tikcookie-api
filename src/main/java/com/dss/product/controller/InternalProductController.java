package com.dss.product.controller;

import com.dss.common.result.Result;
import com.dss.product.model.dto.ProductSaveDTO;
import com.dss.product.model.dto.ProductStatusDTO;
import com.dss.product.model.vo.ProductVO;
import com.dss.product.service.ProductService;
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
 * 内部：录入商品、上下架（请求头 X-Internal-Key）。接口文档 5.9.3–5.9.5。
 */
@Tag(name = "内部：商品")
@RestController
@RequestMapping("/api/v1/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService productService;

    @Operation(summary = "新建商品", description = "新建后直接上架；抢购商品的库存写在 MySQL products.stock；提交后发 product.changed")
    @PostMapping
    public Result<ProductVO> create(@Valid @RequestBody ProductSaveDTO dto) {
        return Result.ok(productService.createProduct(dto));
    }

    @Operation(summary = "修改商品（整体覆盖）", description = "类型和所属店铺不能改；抢购开始后不能改库存、限购和时间窗")
    @PutMapping("/{productId}")
    public Result<ProductVO> update(@PathVariable Long productId, @Valid @RequestBody ProductSaveDTO dto) {
        return Result.ok(productService.updateProduct(productId, dto));
    }

    @Operation(summary = "上下架", description = "下架后 C 端不可见、不能下单；已下的单不受影响")
    @PutMapping("/{productId}/status")
    public Result<Void> changeStatus(@PathVariable Long productId, @Valid @RequestBody ProductStatusDTO dto) {
        productService.changeStatus(productId, dto.getStatus());
        return Result.ok();
    }
}
