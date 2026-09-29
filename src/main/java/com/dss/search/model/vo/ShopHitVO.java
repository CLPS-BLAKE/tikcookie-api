package com.dss.search.model.vo;

import com.dss.shop.model.enums.ShopCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "店铺搜索结果")
public class ShopHitVO {

    @Schema(description = "店铺 ID（字符串）")
    private String id;

    @Schema(description = "店名")
    private String name;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "分类")
    private ShopCategory category;

    @Schema(description = "首张展示图的完整 URL")
    private String coverUrl;

    @Schema(description = "营业时间，可为 null")
    private String businessHours;
}
