package com.dss.product.model.dto;

import com.dss.common.result.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 首页商品流查询参数。排序参数用小写代码，对应枚举 FeedSort。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductFeedQuery extends PageQuery {

    @Pattern(regexp = "latest|sales", message = "sort 只能是 latest 或 sales")
    @Schema(description = "latest 最新（默认）/ sales 销量", example = "latest")
    private String sort = "latest";
}
