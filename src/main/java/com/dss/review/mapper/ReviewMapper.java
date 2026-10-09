package com.dss.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dss.review.model.entity.Review;
import com.dss.review.model.vo.ReviewSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * reviews 表。列表/分页用 BaseMapper + LambdaQueryWrapper，用到的索引：
 * 商品评价 idx_reviews_product_created，店铺评价 idx_reviews_shop_created；唯一索引 uk_reviews_order。
 * <p>
 * 汇总用自定义 SQL（AVG 聚合）；这个查询只读普通列、不涉及 images JSON 列，无需 @ResultMap。
 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /** 商品评价汇总：总数 + 平均分（保留 1 位小数，无评价时 avg 为 0）。 */
    @Select("SELECT COUNT(*) AS review_count, ROUND(IFNULL(AVG(rating), 0), 1) AS avg_rating "
            + "FROM reviews WHERE product_id = #{productId}")
    ReviewSummaryVO selectSummary(@Param("productId") Long productId);
}
