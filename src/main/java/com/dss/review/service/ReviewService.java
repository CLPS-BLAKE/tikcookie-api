package com.dss.review.service;

import com.dss.common.result.PageQuery;
import com.dss.common.result.PageResult;
import com.dss.review.model.dto.ReviewCreateDTO;
import com.dss.review.model.vo.ReviewSummaryVO;
import com.dss.review.model.vo.ReviewVO;

/**
 * 订单评价。规则见需求文档 4.4、接口文档 5.7。
 */
public interface ReviewService {

    /**
     * 提交评价：订单不存在或不是本人 104001；订单非 USED 108001；已经评价过 108002。
     */
    ReviewVO createReview(Long userId, Long orderId, ReviewCreateDTO dto);

    /**
     * 我该订单的评价；未评价返回 null。
     */
    ReviewVO getMyReview(Long userId, Long orderId);

    /**
     * 商品评价列表（公开），按评价时间倒序分页。
     */
    PageResult<ReviewVO> listProductReviews(Long productId, PageQuery query);

    /**
     * 商品评价汇总（公开）：总数 + 平均分（保留 1 位小数）。
     */
    ReviewSummaryVO getProductReviewSummary(Long productId);
}
