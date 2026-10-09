package com.dss.review.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "商品评价汇总")
public class ReviewSummaryVO {

    @Schema(description = "评价总数")
    private Integer reviewCount;

    @Schema(description = "平均评分（保留 1 位小数），无评价时为 0", example = "4.6")
    private Double avgRating;
}
