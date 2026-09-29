package com.dss.search.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品搜索排序。接口里传小写代码 code。
 */
@Getter
@AllArgsConstructor
public enum SearchSort {

    DEFAULT("default", "综合：先相关度，再已售数"),
    SALES("sales", "已售数倒序"),
    PRICE_ASC("price_asc", "价格从低到高"),
    PRICE_DESC("price_desc", "价格从高到低");

    private final String code;
    private final String desc;

    /** 按小写代码取枚举；不认识的代码按 DEFAULT 处理。 */
    public static SearchSort fromCode(String code) {
        for (SearchSort sort : values()) {
            if (sort.code.equals(code)) {
                return sort;
            }
        }
        return DEFAULT;
    }
}
