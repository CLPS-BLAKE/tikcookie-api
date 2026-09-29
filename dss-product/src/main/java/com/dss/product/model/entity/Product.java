package com.dss.product.model.entity;

import com.dss.product.model.enums.ProductStatus;
import com.dss.product.model.enums.ProductType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品（集合 products）。只有一个金额 price（分），不分原价和团购价。
 * 字段说明见 docs/中间件配置.md 4.4。
 */
@Data
@Document(collection = "products")
public class Product {

    /** 商品 ID，RedisIdGenerator 生成（biz=product）。 */
    @Id
    private Long id;

    /** 所属店铺，创建后不能改。 */
    private Long shopId;

    private String name;

    /** 套餐内容（即最初设计里的"菜品"），可以是空数组。 */
    private List<ContentGroup> contents;

    /** 金额（分）。 */
    private Long price;

    /** 创建后不能改。 */
    private ProductType type;

    /** 展示图 fileId。 */
    private String image;

    private ProductStatus status;

    /** 已售数：支付 +1，退款 −1。 */
    private Integer soldCount;

    /** 支付后几天内有效：expireTime = payTime + validDays 天。 */
    private Integer validDays;

    /** 使用规则，如"周末节假日通用"，可以是空数组。 */
    private List<String> useRules;

    // ---------- 以下仅 FLASH（抢购）有值 ----------

    /** Mongo 里的剩余库存：落单时扣，取消待支付订单时回补。实时剩余以 Redis 为准。 */
    private Integer stock;

    private LocalDateTime flashStartTime;

    private LocalDateTime flashEndTime;

    /** 每人限购，默认 1。 */
    private Integer limitPerUser;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
