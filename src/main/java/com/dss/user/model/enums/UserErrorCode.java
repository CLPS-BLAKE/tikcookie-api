package com.dss.user.model.enums;

import com.dss.common.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户模块业务码 101xxx。
 */
@Getter
@AllArgsConstructor
public enum UserErrorCode implements ErrorCode {

    SMS_CODE_TOO_FREQUENT(101001, "验证码发送太频繁，请 60 秒后再试"),
    SMS_CODE_INVALID(101002, "验证码错误或已过期"),
    SMS_CODE_LOCKED(101003, "验证码错误次数过多，请重新获取"),
    USER_DISABLED(101004, "账号已被禁用"),
    USER_NOT_FOUND(101005, "用户不存在");

    private final int code;
    private final String msg;
}
