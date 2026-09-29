package com.dss.common.context;

import com.dss.common.error.CommonErrorCode;
import com.dss.common.exception.BizException;

/**
 * 当前登录用户的 ThreadLocal 容器。请求结束时由登录拦截器清理。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 当前登录用户；公开接口里可能为 null。 */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 当前登录用户 ID；没登录时抛 401。只应在需要登录的接口里调用。 */
    public static Long requireUserId() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED);
        }
        return user.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
