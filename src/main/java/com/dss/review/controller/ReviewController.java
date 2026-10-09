package com.dss.review.controller;

import com.dss.common.config.OpenApiConfig;
import com.dss.common.context.UserContext;
import com.dss.common.result.PageQuery;
import com.dss.common.result.PageResult;
import com.dss.common.result.Result;
import com.dss.review.model.dto.ReviewCreateDTO;
import com.dss.review.model.vo.ReviewSummaryVO;
import com.dss.review.model.vo.ReviewVO;
import com.dss.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单评价。提交/查我评价挂在订单下（需登录），商品评价列表与汇总公开。接口文档 5.7。
 */
@Tag(name = "评价")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "提交评价", description = "订单核销（USED）后才能评价；一笔订单只能评价一次")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/orders/{orderId}/review")
    public Result<ReviewVO> create(@PathVariable Long orderId, @Valid @RequestBody ReviewCreateDTO dto) {
        return Result.ok(reviewService.createReview(UserContext.requireUserId(), orderId, dto));
    }

    @Operation(summary = "我该订单的评价", description = "未评价时 data 为 null")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/orders/{orderId}/review")
    public Result<ReviewVO> myReview(@PathVariable Long orderId) {
        return Result.ok(reviewService.getMyReview(UserContext.requireUserId(), orderId));
    }

    @Operation(summary = "商品评价列表（公开）", description = "按评价时间倒序分页")
    @GetMapping("/products/{productId}/reviews")
    public Result<PageResult<ReviewVO>> list(@PathVariable Long productId, @Valid @ParameterObject PageQuery query) {
        return Result.ok(reviewService.listProductReviews(productId, query));
    }

    @Operation(summary = "商品评价汇总（公开）", description = "返回评价总数与平均分（保留 1 位小数）")
    @GetMapping("/products/{productId}/review-summary")
    public Result<ReviewSummaryVO> summary(@PathVariable Long productId) {
        return Result.ok(reviewService.getProductReviewSummary(productId));
    }
}
