package com.dss.common.exception;

import com.dss.common.error.CommonErrorCode;

/**
 * 骨架期占位：业务方法还没实现时抛出，接口返回 HTTP 501、code 50100。
 */
public class NotImplementedException extends BizException {

    public NotImplementedException() {
        super(CommonErrorCode.NOT_IMPLEMENTED);
    }
}
