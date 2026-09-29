package com.dss.search.model.vo;

import com.dss.product.model.enums.ProductType;
import com.dss.shop.model.enums.ShopCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "商品搜索结果")
public class ProductHitVO {

    @Schema(description = "商品 ID（字符串）")
    private String id;

    @Schema(description = "商品名")
    private String name;

    @Schema(description = "店铺 ID（字符串）")
    private String shopId;

    @Schema(description = "店名")
    private String shopName;

    @Schema(description = "所属店铺的分类")
    private ShopCategory shopCategory;

    @Schema(description = "商品类型")
    private ProductType type;

    @Schema(description = "金额（分）")
    private Long price;

    @Schema(description = "已售数")
    private Integer soldCount;

    @Schema(description = "展示图完整 URL")
    private String imageUrl;

    @Schema(description = "开抢时间，仅 FLASH")
    private LocalDateTime flashStartTime;

    @Schema(description = "结束时间，仅 FLASH")
    private LocalDateTime flashEndTime;
}
