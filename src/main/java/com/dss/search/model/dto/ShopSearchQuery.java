package com.dss.search.model.dto;

import com.dss.common.result.PageQuery;
import com.dss.shop.model.enums.ShopCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺搜索参数。关键词为空时就是按分类浏览。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopSearchQuery extends PageQuery {

    @Size(max = 50, message = "关键词最多 50 字")
    @Schema(description = "关键词，可为空；匹配店名和地址", example = "牛肉面")
    private String keyword;

    @Schema(description = "按分类筛选", example = "FOOD")
    private ShopCategory category;
}
