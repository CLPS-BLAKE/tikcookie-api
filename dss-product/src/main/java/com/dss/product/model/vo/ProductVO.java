package com.dss.product.model.vo;

import com.dss.product.model.entity.ContentGroup;
import com.dss.product.model.enums.ProductStatus;
import com.dss.product.model.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "商品详情")
public class ProductVO {

    @Schema(description = "商品 ID（字符串）", example = "101486725911707649")
    private String id;

    @Schema(description = "店铺 ID（字符串）")
    private String shopId;

    @Schema(description = "店名")
    private String shopName;

    @Schema(description = "店铺地址")
    private String shopAddress;

    @Schema(description = "商品名")
    private String name;

    @Schema(description = "套餐内容")
    private List<ContentGroup> contents;

    @Schema(description = "金额（分）", example = "3990")
    private Long price;

    @Schema(description = "商品类型")
    private ProductType type;

    @Schema(description = "展示图 fileId")
    private String image;

    @Schema(description = "展示图完整 URL")
    private String imageUrl;

    @Schema(description = "上下架状态")
    private ProductStatus status;

    @Schema(description = "已售数")
    private Integer soldCount;

    @Schema(description = "支付后几天内有效")
    private Integer validDays;

    @Schema(description = "使用规则")
    private List<String> useRules;

    @Schema(description = "剩余库存（以 Redis 为准），仅 FLASH")
    private Integer remainingStock;

    @Schema(description = "开抢时间，仅 FLASH")
    private LocalDateTime flashStartTime;

    @Schema(description = "结束时间，仅 FLASH")
    private LocalDateTime flashEndTime;

    @Schema(description = "每人限购，仅 FLASH")
    private Integer limitPerUser;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
