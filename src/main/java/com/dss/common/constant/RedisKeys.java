package com.dss.common.constant;

import java.time.Duration;

/**
 * 全部 Redis key（统一前缀 dss:），用途和 TTL 见 docs/中间件配置.md 第 4 节。
 * Redis 只放短期登录数据；数据库 ID、抢购库存、已购计数都不放 Redis。
 */
public final class RedisKeys {

    public static final String PREFIX = "dss:";

    /** 验证码有效期 5 分钟。 */
    public static final Duration LOGIN_CODE_TTL = Duration.ofMinutes(5);
    /** 同一手机号 60 秒内不能重发。 */
    public static final Duration LOGIN_CODE_INTERVAL = Duration.ofSeconds(60);
    /** 同一验证码累计错 5 次作废。 */
    public static final int LOGIN_CODE_MAX_FAIL = 5;

    /** 登录态 hash 的字段。 */
    public static final String TOKEN_FIELD_USER_ID = "userId";
    public static final String TOKEN_FIELD_NICKNAME = "nickname";
    public static final String TOKEN_FIELD_AVATAR = "avatar";

    private RedisKeys() {
    }

    /** 验证码，string，TTL 300 秒；重发覆盖。 */
    public static String loginCode(String phone) {
        return PREFIX + "login:code:" + phone;
    }

    /** 重发间隔标记，string，TTL 60 秒。 */
    public static String loginCodeInterval(String phone) {
        return PREFIX + "login:code:interval:" + phone;
    }

    /** 校验失败次数，string 计数，TTL 300 秒。 */
    public static String loginCodeFail(String phone) {
        return PREFIX + "login:code:fail:" + phone;
    }

    /** 登录态，hash（userId、nickname、avatar），TTL 7 天、滑动续期。 */
    public static String loginToken(String token) {
        return PREFIX + "login:token:" + token;
    }
}
