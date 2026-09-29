package com.dss.search.model.enums;

import com.dss.common.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 搜索模块业务码 107xxx。
 */
@Getter
@AllArgsConstructor
public enum SearchErrorCode implements ErrorCode {

    SEARCH_UNAVAILABLE(107001, "搜索服务暂不可用");

    private final int code;
    private final String msg;
}
