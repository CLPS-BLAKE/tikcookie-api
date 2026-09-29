package com.dss.favorite.model.vo;

import com.dss.favorite.model.enums.TargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "收藏项")
public class FavoriteVO {

    @Schema(description = "收藏目标类型")
    private TargetType targetType;

    @Schema(description = "店铺 ID 或商品 ID（字符串）")
    private String targetId;

    @Schema(description = "店名或商品名")
    private String name;

    @Schema(description = "店铺首图或商品图的完整 URL")
    private String imageUrl;

    @Schema(description = "商品金额（分），仅 PRODUCT")
    private Long price;

    @Schema(description = "商品是否已下架，仅 PRODUCT")
    private Boolean offShelf;

    @Schema(description = "收藏时间")
    private LocalDateTime createTime;
}
