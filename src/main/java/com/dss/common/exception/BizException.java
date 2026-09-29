package com.dss.common.exception;

import com.dss.common.error.ErrorCode;
import lombok.Getter;

/**
 * 业务异常：由 {@link GlobalExceptionHandler} 转成 {code, msg, data}，HTTP 状态取错误码里的值。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
