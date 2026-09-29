package com.dss.product.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductStatus {

    ON_SHELF("上架"),
    OFF_SHELF("下架");

    private final String desc;
}
