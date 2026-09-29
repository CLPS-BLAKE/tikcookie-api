package com.dss.product.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductType {

    /** 普通：不限量，不维护库存。 */
    NORMAL("普通"),
    /** 抢购：限量、限时、每人限购。 */
    FLASH("抢购");

    private final String desc;
}
