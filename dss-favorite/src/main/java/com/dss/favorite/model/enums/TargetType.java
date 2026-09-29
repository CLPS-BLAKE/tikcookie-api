package com.dss.favorite.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TargetType {

    SHOP("店铺"),
    PRODUCT("商品");

    private final String desc;
}
