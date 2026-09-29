package com.dss.common.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用码：和 HTTP 状态一一对应。
 */
@Getter
@AllArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    SUCCESS(0, "ok", 200),
    BAD_REQUEST(40000, "参数错误", 400),
    UNAUTHORIZED(40100, "未登录或登录已过期", 401),
    INTERNAL_KEY_INVALID(40300, "内部口令错误", 403),
    NOT_FOUND(40400, "资源不存在", 404),
    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试", 500),
    NOT_IMPLEMENTED(50100, "功能未实现", 501);

    private final int code;
    private final String msg;
    private final int httpStatus;
}
