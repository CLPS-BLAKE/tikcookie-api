package com.dss.product.model.vo;

import com.dss.product.model.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "商品卡片（列表用）")
public class ProductCardVO {

    @Schema(description = "商品 ID（字符串）")
    private String id;

    @Schema(description = "店铺 ID（字符串）")
    private String shopId;

    @Schema(description = "店名")
    private String shopName;

    @Schema(description = "商品名")
    private String name;

    @Schema(description = "金额（分）")
    private Long price;

    @Schema(description = "商品类型")
    private ProductType type;

    @Schema(description = "展示图完整 URL")
    private String imageUrl;

    @Schema(description = "已售数")
    private Integer soldCount;
}
