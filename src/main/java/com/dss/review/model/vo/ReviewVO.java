package com.dss.review.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "订单评价")
public class ReviewVO {

    @Schema(description = "评价 ID（字符串）")
    private String id;

    @Schema(description = "订单 ID（字符串）")
    private String orderId;

    @Schema(description = "评价用户 ID（字符串）")
    private String userId;

    @Schema(description = "评价用户昵称；匿名时为 null")
    private String userNickname;

    @Schema(description = "评价用户头像完整 URL；匿名时为 null")
    private String userAvatarUrl;

    @Schema(description = "商品 ID（字符串）")
    private String productId;

    @Schema(description = "店铺 ID（字符串）")
    private String shopId;

    @Schema(description = "评分 1-5 星")
    private Integer rating;

    @Schema(description = "文字评价，可为空")
    private String content;

    @Schema(description = "评价图片完整 URL 数组")
    private List<String> imageUrls;

    @Schema(description = "是否匿名")
    private Boolean anonymous;

    @Schema(description = "评价时间")
    private LocalDateTime createTime;
}
