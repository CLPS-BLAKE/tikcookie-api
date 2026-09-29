package com.dss.common.error;

/**
 * 错误码。通用码见 {@link CommonErrorCode}；各模块的业务码是 6 位：1 + 2 位模块号 + 3 位序号，HTTP 状态一律 200。
 */
public interface ErrorCode {

    int getCode();

    String getMsg();

    /**
     * 对应的 HTTP 状态码；业务错误默认 200。
     */
    default int getHttpStatus() {
        return 200;
    }
}
