package com.dss.favorite.model.enums;

import com.dss.common.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 收藏模块业务码 105xxx。
 */
@Getter
@AllArgsConstructor
public enum FavoriteErrorCode implements ErrorCode {

    FAVORITE_TARGET_NOT_FOUND(105001, "收藏的目标不存在"),
    FAVORITE_ALREADY_EXISTS(105002, "已经收藏过了");

    private final int code;
    private final String msg;
}
