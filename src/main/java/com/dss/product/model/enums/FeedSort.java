package com.dss.product.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 首页商品流排序。接口里传小写代码 code。
 */
@Getter
@AllArgsConstructor
public enum FeedSort {

    LATEST("latest", "最新"),
    SALES("sales", "销量");

    private final String code;
    private final String desc;

    /** 按小写代码取枚举；不认识的代码按 LATEST 处理。 */
    public static FeedSort fromCode(String code) {
        for (FeedSort sort : values()) {
            if (sort.code.equals(code)) {
                return sort;
            }
        }
        return LATEST;
    }
}
