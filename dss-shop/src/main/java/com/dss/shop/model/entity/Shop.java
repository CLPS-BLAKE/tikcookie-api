package com.dss.shop.model.entity;

import com.dss.shop.model.enums.ShopCategory;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺（集合 shops）。不存经纬度，所以没有"附近"。字段说明见 docs/中间件配置.md 4.3。
 */
@Data
@Document(collection = "shops")
public class Shop {

    /** 店铺 ID，RedisIdGenerator 生成（biz=shop）。 */
    @Id
    private Long id;

    private String name;

    private String address;

    private ShopCategory category;

    /** 展示图 fileId，1–9 张。 */
    private List<String> images;

    /** 营业时间，如 10:00-22:00，可为空。 */
    private String businessHours;

    /** 电话，可为空。 */
    private String phone;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
