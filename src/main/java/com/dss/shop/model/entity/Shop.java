package com.dss.shop.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dss.shop.model.enums.ShopCategory;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺（表 shops）。不存经纬度，所以没有"附近"。表结构见 dss-init.sql。
 * images 是 JSON 列：autoResultMap = true，BaseMapper 查出来时才会按 TypeHandler 反序列化；
 * 自己写的 @Select 要加 @ResultMap("mybatis-plus_Shop")。
 */
@Data
@TableName(value = "shops", autoResultMap = true)
public class Shop {

    /** 店铺 ID，MySQL 自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String address;

    private ShopCategory category;

    /** 展示图 fileId（OSS ObjectKey）数组，按展示顺序，1–9 张。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /** 营业时间，如 10:00-22:00，可为空。 */
    private String businessHours;

    /** 电话，可为空。 */
    private String phone;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
