package com.dss.shop.model.enums;

import com.dss.common.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 店铺模块业务码 102xxx。
 */
@Getter
@AllArgsConstructor
public enum ShopErrorCode implements ErrorCode {

    SHOP_NOT_FOUND(102001, "店铺不存在");

    private final int code;
    private final String msg;
}
