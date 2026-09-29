package com.dss.product.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "抢购卡片")
public class FlashProductVO {

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

    @Schema(description = "展示图完整 URL")
    private String imageUrl;

    @Schema(description = "剩余库存（Redis）")
    private Integer remainingStock;

    @Schema(description = "开抢时间")
    private LocalDateTime flashStartTime;

    @Schema(description = "结束时间")
    private LocalDateTime flashEndTime;

    @Schema(description = "每人限购")
    private Integer limitPerUser;

    @Schema(description = "true 进行中 / false 即将开始")
    private Boolean ongoing;
}
