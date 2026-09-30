package com.dss.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * 自定义配置 dss.*，对应的环境变量见 docs/中间件配置.md 第 2 节。
 */
@Data
@ConfigurationProperties(prefix = "dss")
public class DssProperties {

    private InternalProperties internal = new InternalProperties();
    private AuthProperties auth = new AuthProperties();
    private FileProperties file = new FileProperties();
    private OrderProperties order = new OrderProperties();
    private JobProperties job = new JobProperties();
    private SearchProperties search = new SearchProperties();

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
        /**
         * 图片公共读取前缀（末尾不带 /）：返回给前端的 url = baseUrl + "/" + fileId。
         * Bucket 公共读时填 OSS 公共域名（或自己的 CNAME 域名），如
         * https://blake-tikcookie.oss-cn-guangzhou.aliyuncs.com；留空时由
         * {@link com.dss.common.file.FileUrlResolver} 按 oss.endpoint + bucket 自动推导。
         * 也可以填后端代理读图接口（如 http://127.0.0.1:8080/api/v1/images），图片走后端转发。
         */
        private String baseUrl;
        /** 单张图片上限。 */
        private DataSize maxSize = DataSize.ofMegabytes(5);
        /** 允许的 Content-Type。 */
        private List<String> allowedTypes = List.of("image/jpeg", "image/png", "image/webp");
        private OssProperties oss = new OssProperties();
    }

    @Data
    public static class OssProperties {
        /** 如 https://oss-cn-hangzhou.aliyuncs.com。 */
        private String endpoint;
        private String bucket;
        /** 如 cn-hangzhou。 */
        private String region;
        /** 演示环境可用最小权限的专用 RAM 用户；实际值只放在受控环境文件里。 */
        private String accessKeyId;
        private String accessKeySecret;
    }

    @Data
    public static class OrderProperties {
        /** 待支付超时分钟数：payDeadline = 下单时间 + 它。 */
        private int payTimeoutMinutes = 15;
    }

    @Data
    public static class JobProperties {
        /** 订单定时任务总开关；骨架期关闭。单实例执行，不加分布式锁。 */
        private boolean enabled = false;
        private Duration orderTimeoutInterval = Duration.ofMinutes(1);
        private Duration voucherExpireInterval = Duration.ofMinutes(1);
    }

    @Data
    public static class SearchProperties {
        /** 为 true 时启动后清空并从 MySQL 全量重建两个 ES 索引；只给部署方用。 */
        private boolean rebuildOnStartup = false;
    }
}
