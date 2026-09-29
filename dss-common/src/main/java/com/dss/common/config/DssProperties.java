package com.dss.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * 自定义配置 dss.*，默认值和环境变量见 docs/中间件配置.md 2.4。
 */
@Data
@ConfigurationProperties(prefix = "dss")
public class DssProperties {

    private InternalProperties internal = new InternalProperties();
    private AuthProperties auth = new AuthProperties();
    private FileProperties file = new FileProperties();
    private OrderProperties order = new OrderProperties();
    private JobProperties job = new JobProperties();

    @Data
    public static class InternalProperties {
        /** 内部接口口令（请求头 X-Internal-Key）；为空时内部接口一律拒绝。 */
        private String key;
    }

    @Data
    public static class AuthProperties {
        /** 登录态有效期，每次访问续满（滑动过期）。 */
        private Duration tokenTtl = Duration.ofDays(7);
    }

    @Data
    public static class FileProperties {
        /** fileId 前面拼上它就是完整 URL；为空时得到以 / 开头的站点相对地址（由 Nginx 反代到 FastDFS）。 */
        private String baseUrl = "";
        /** 单张图片上限。 */
        private DataSize maxSize = DataSize.ofMegabytes(5);
        /** 允许的 Content-Type。 */
        private List<String> allowedTypes = List.of("image/jpeg", "image/png", "image/webp");
        private FastDfsProperties fastdfs = new FastDfsProperties();
    }

    @Data
    public static class FastDfsProperties {
        private List<String> trackerServers = List.of();
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration networkTimeout = Duration.ofSeconds(30);
    }

    @Data
    public static class OrderProperties {
        /** 待支付超时分钟数，同时决定延迟队列 order.timeout.delay 的 TTL。 */
        private int payTimeoutMinutes = 15;
    }

    @Data
    public static class JobProperties {
        /** 定时任务总开关；骨架期关闭。 */
        private boolean enabled = false;
        private Duration timeoutFallbackInterval = Duration.ofMinutes(5);
        private Duration voucherExpireInterval = Duration.ofMinutes(10);
    }
}
