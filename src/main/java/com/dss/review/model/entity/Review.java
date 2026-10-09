package com.dss.review.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单评价（表 reviews）。一笔订单最多一条（唯一索引 uk_reviews_order）。表结构见 dss-init.sql。
 * images 是 JSON 列（评价图片 ObjectKey 数组，0-3 张）：autoResultMap = true，BaseMapper 查出来时
 * 才会按 TypeHandler 反序列化；自己写的 @Select 要加 @ResultMap("mybatis-plus_Review")。
 */
@Data
@TableName(value = "reviews", autoResultMap = true)
public class Review {

    /** 评价 ID，MySQL 自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单 ID，唯一。 */
    private Long orderId;

    /** 评价用户 ID。 */
    private Long userId;

    /** 商品 ID。 */
    private Long productId;

    /** 店铺 ID。 */
    private Long shopId;

    /** 评分 1-5 星。 */
    private Integer rating;

    /** 文字评价，可为空。 */
    private String content;

    /** 评价图片 fileId（OSS ObjectKey）数组，0-3 张。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /** 是否匿名；匿名时公开列表不展示昵称/头像。 */
    private Boolean anonymous;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
