package com.dss.common.id;

import com.dss.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局 long ID 生成器（已实现）：id = (当前秒 − 起始秒) << 32 | 当日序列。
 * 序列来自 Redis INCR dss:id:{biz}:{yyyyMMdd}，多副本部署也不用分配 workerId。
 * 生成的 ID 超过 JS 的 2^53，接口里一律当字符串传。
 */
@Component
@RequiredArgsConstructor
public class RedisIdGenerator {

    public static final String BIZ_USER = "user";
    public static final String BIZ_SHOP = "shop";
    public static final String BIZ_PRODUCT = "product";
    public static final String BIZ_ORDER = "order";
    public static final String BIZ_FAVORITE = "favorite";

    /** 起始秒：2026-01-01 00:00:00（东八区）。 */
    private static final long BEGIN_EPOCH_SECOND = 1767196800L;
    private static final int SEQUENCE_BITS = 32;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 序列 key 保留 2 天后自动过期。 */
    private static final Duration SEQUENCE_KEY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redisTemplate;

    /**
     * @param biz 业务名，取 BIZ_* 常量
     */
    public long nextId(String biz) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        long timestamp = now.toEpochSecond() - BEGIN_EPOCH_SECOND;
        String key = RedisKeys.idSequence(biz, now.format(DAY));
        Long sequence = redisTemplate.opsForValue().increment(key);
        if (sequence == null) {
            throw new IllegalStateException("Redis 自增失败：" + key);
        }
        if (sequence == 1L) {
            redisTemplate.expire(key, SEQUENCE_KEY_TTL);
        }
        return timestamp << SEQUENCE_BITS | sequence;
    }
}
