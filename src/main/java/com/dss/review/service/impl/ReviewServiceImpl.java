package com.dss.review.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dss.common.exception.BizException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.result.PageQuery;
import com.dss.common.result.PageResult;
import com.dss.order.model.entity.Order;
import com.dss.order.model.enums.OrderStatus;
import com.dss.order.service.OrderService;
import com.dss.review.mapper.ReviewMapper;
import com.dss.review.model.dto.ReviewCreateDTO;
import com.dss.review.model.entity.Review;
import com.dss.review.model.enums.ReviewErrorCode;
import com.dss.review.model.vo.ReviewSummaryVO;
import com.dss.review.model.vo.ReviewVO;
import com.dss.review.service.ReviewService;
import com.dss.user.model.entity.User;
import com.dss.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 评价实现。提交评价：先经订单服务校验本人 + 状态（USED），再由 order_id 唯一索引兜底防重复。
 * 列表与汇总只读 reviews 表（用户名/头像批量查用户），不反向依赖商品/店铺模块。
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderService orderService;
    private final UserService userService;
    private final FileUrlResolver fileUrlResolver;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO createReview(Long userId, Long orderId, ReviewCreateDTO dto) {
        // 订单不存在或不是本人：104001（订单模块抛出）
        Order order = orderService.requireMyOrder(userId, orderId);
        if (order.getStatus() != OrderStatus.USED) {
            throw new BizException(ReviewErrorCode.REVIEW_ORDER_NOT_USED);
        }
        Review review = new Review();
        review.setOrderId(orderId);
        review.setUserId(userId);
        review.setProductId(order.getProductId());
        review.setShopId(order.getShopId());
        review.setRating(dto.getRating());
        review.setContent(StringUtils.hasText(dto.getContent()) ? dto.getContent().trim() : null);
        review.setImages(dto.getImages());
        review.setAnonymous(Boolean.TRUE.equals(dto.getAnonymous()));
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：唯一索引 uk_reviews_order 兜底
            throw new BizException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        }
        return toVO(reviewMapper.selectById(review.getId()),
                userService.getUsersByIds(List.of(userId)).get(userId));
    }

    @Override
    public ReviewVO getMyReview(Long userId, Long orderId) {
        Review review = reviewMapper.selectOne(Wrappers.<Review>lambdaQuery()
                .eq(Review::getOrderId, orderId)
                .eq(Review::getUserId, userId));
        if (review == null) {
            return null;
        }
        return toVO(review, userService.getUsersByIds(List.of(userId)).get(userId));
    }

    @Override
    public PageResult<ReviewVO> listProductReviews(Long productId, PageQuery query) {
        Page<Review> page = reviewMapper.selectPage(Page.of(query.getPage(), query.getSize()),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getProductId, productId)
                        .orderByDesc(Review::getCreatedAt)
                        .orderByDesc(Review::getId));
        if (page.getRecords().isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), query.getPage(), query.getSize());
        }
        Map<Long, User> users = userService.getUsersByIds(page.getRecords().stream()
                .map(Review::getUserId).distinct().toList());
        List<ReviewVO> list = new ArrayList<>(page.getRecords().size());
        for (Review review : page.getRecords()) {
            list.add(toVO(review, users.get(review.getUserId())));
        }
        return PageResult.of(list, page.getTotal(), query.getPage(), query.getSize());
    }

    @Override
    public ReviewSummaryVO getProductReviewSummary(Long productId) {
        return reviewMapper.selectSummary(productId);
    }

    private ReviewVO toVO(Review review, User user) {
        ReviewVO vo = new ReviewVO();
        vo.setId(String.valueOf(review.getId()));
        vo.setOrderId(String.valueOf(review.getOrderId()));
        vo.setUserId(String.valueOf(review.getUserId()));
        vo.setProductId(String.valueOf(review.getProductId()));
        vo.setShopId(String.valueOf(review.getShopId()));
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setImageUrls(fileUrlResolver.toUrls(review.getImages()));
        boolean anonymous = Boolean.TRUE.equals(review.getAnonymous());
        vo.setAnonymous(anonymous);
        if (!anonymous && user != null) {
            vo.setUserNickname(user.getNickname());
            vo.setUserAvatarUrl(fileUrlResolver.toUrl(user.getAvatar()));
        }
        vo.setCreateTime(review.getCreatedAt());
        return vo;
    }
}
