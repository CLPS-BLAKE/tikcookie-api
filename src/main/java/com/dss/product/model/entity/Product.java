package com.dss.product.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dss.product.model.enums.ProductStatus;
import com.dss.product.model.enums.ProductType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品（表 products）。只有一个金额 price（分），不分原价和团购价。表结构见 dss-init.sql。
 * contents、useRules 是 JSON 列：autoResultMap = true，BaseMapper 查出来时才会按 TypeHandler 反序列化；
 * 自己写的 @Select 要加 @ResultMap("mybatis-plus_Product")。
 */
@Data
@TableName(value = "products", autoResultMap = true)
public class Product {

    /** 商品 ID，MySQL 自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属店铺，创建后不能改。 */
    private Long shopId;

    private String name;

    /** 套餐内容（即最初设计里的"菜品"），可以是空数组。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ContentGroup> contents;

    /** 金额（分）。 */
    private Long price;

    /** 创建后不能改。 */
    private ProductType type;

    /** 展示图 fileId（OSS ObjectKey）。 */
    private String image;

    private ProductStatus status;

    /** 已售数：支付 +1，退款 −1。 */
    private Integer soldCount;

    /** 支付后几天内有效：expiresAt = paidAt + validDays 天。 */
    private Integer validDays;

    /** 使用规则，如"周末节假日通用"，可以是空数组。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> useRules;

    // ---------- 以下仅 FLASH（抢购）有值，NORMAL 为 NULL ----------

    /** 权威剩余库存：抢购下单时在事务里扣 1，取消待支付的抢购单时回补 1；退款不回补。 */
    private Integer stock;

    private LocalDateTime flashStartTime;

    private LocalDateTime flashEndTime;

    /** 每人限购，默认 1。计数含 UNPAID / UNUSED / USED / REFUNDED，不含 CANCELLED。 */
    private Integer limitPerUser;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
