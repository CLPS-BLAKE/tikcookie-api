package com.dss.product.model.enums;

import com.dss.common.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品模块业务码 103xxx（抢购下单时的时间窗、库存、限购错误也在这里，由订单模块抛出）。
 */
@Getter
@AllArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND(103001, "商品不存在"),
    PRODUCT_OFF_SHELF(103002, "商品已下架"),
    PRODUCT_TYPE_IMMUTABLE(103003, "商品类型创建后不能修改"),
    PRODUCT_SHOP_IMMUTABLE(103004, "商品所属店铺不能修改"),
    FLASH_PARAMS_REQUIRED(103005, "抢购商品必须填写库存、开抢时间和结束时间"),
    FLASH_TIME_INVALID(103006, "结束时间必须晚于开抢时间"),
    FLASH_STARTED_LOCKED(103007, "抢购已开始，不能修改库存、限购和时间"),
    FLASH_NOT_STARTED(103008, "抢购还没开始"),
    FLASH_ENDED(103009, "抢购已结束"),
    FLASH_SOLD_OUT(103010, "已抢光"),
    FLASH_LIMIT_EXCEEDED(103011, "超过每人限购数量"),
    NOT_FLASH_PRODUCT(103012, "该商品不是抢购商品，请走普通下单"),
    NOT_NORMAL_PRODUCT(103013, "该商品是抢购商品，请走抢购下单");

    private final int code;
    private final String msg;
}
