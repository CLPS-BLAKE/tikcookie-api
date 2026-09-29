package com.dss.search.model.dto;

import com.dss.common.result.PageQuery;
import com.dss.product.model.enums.ProductType;
import com.dss.shop.model.enums.ShopCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品搜索参数。排序用小写代码，对应枚举 SearchSort。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductSearchQuery extends PageQuery {

    @Size(max = 50, message = "关键词最多 50 字")
    @Schema(description = "关键词，可为空；匹配商品名、套餐内容、店铺名", example = "牛肉面")
    private String keyword;

    @Schema(description = "按所属店铺的分类筛选", example = "FOOD")
    private ShopCategory category;

    @Schema(description = "按商品类型筛选", example = "NORMAL")
    private ProductType type;

    @Pattern(regexp = "default|sales|price_asc|price_desc", message = "sort 只能是 default、sales、price_asc、price_desc")
    @Schema(description = "default 综合（默认）/ sales 销量 / price_asc 价格从低到高 / price_desc 价格从高到低", example = "default")
    private String sort = "default";
}
