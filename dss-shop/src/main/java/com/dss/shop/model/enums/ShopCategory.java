package com.dss.shop.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 店铺分类。只存在于代码里，不建集合、不入库（数据库不填任何数据）。取值参照抖省省。
 */
@Getter
@AllArgsConstructor
public enum ShopCategory {

    FOOD("餐饮美食"),
    DESSERT_DRINK("甜点饮品"),
    SNACK("快餐小吃"),
    SUPERMARKET("商超购物"),
    LEISURE("休闲娱乐"),
    STAY("住宿游玩");

    private final String desc;
}
