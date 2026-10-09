package com.dss.common.file;

import com.dss.common.config.DssProperties;
import com.dss.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * fileId → URL 的转换约定（中间件配置第 6 节）：url = 图片公共前缀 + "/" + fileId。
 * 前缀优先取 dss.file.base-url，没配时按 dss.file.oss.endpoint + bucket 推导 OSS 公共读域名。
 * user / shop / product 都用它拼图片地址，所以要处理空值、前后斜杠和数组顺序。
 */
class FileUrlResolverTest {

    private static final String KEY = "group1/M00/00/00/0123456789abcdef0123456789abcdef.jpg";

    @Test
    @DisplayName("基础地址 + / + fileId")
    void simpleJoin() {
        FileUrlResolver resolver = resolver("https://demo.oss-cn-hangzhou.aliyuncs.com");

        assertThat(resolver.toUrl(KEY)).isEqualTo("https://demo.oss-cn-hangzhou.aliyuncs.com/" + KEY);
    }

    @Test
    @DisplayName("基础地址末尾带 / 时不会出现双斜杠")
    void trailingSlashInBaseUrl() {
        FileUrlResolver resolver = resolver("https://demo.oss-cn-hangzhou.aliyuncs.com/");

        assertThat(resolver.toUrl(KEY)).isEqualTo("https://demo.oss-cn-hangzhou.aliyuncs.com/" + KEY);
    }

    @Test
    @DisplayName("fileId 以 / 开头时也不会出现双斜杠")
    void leadingSlashInFileId() {
        FileUrlResolver resolver = resolver("https://demo.oss-cn-hangzhou.aliyuncs.com");

        assertThat(resolver.toUrl("/" + KEY)).isEqualTo("https://demo.oss-cn-hangzhou.aliyuncs.com/" + KEY);
    }

    @Test
    @DisplayName("基础地址末尾有多个 / 时也只保留一个")
    void bothSlashes() {
        FileUrlResolver resolver = resolver("https://demo.oss-cn-hangzhou.aliyuncs.com///");

        assertThat(resolver.toUrl("/" + KEY)).isEqualTo("https://demo.oss-cn-hangzhou.aliyuncs.com/" + KEY);
    }

    @Test
    @DisplayName("fileId 为空或空白：返回 null，不拼出半个地址")
    void blankFileId() {
        FileUrlResolver resolver = resolver("https://demo.oss-cn-hangzhou.aliyuncs.com");

        assertThat(resolver.toUrl(null)).isNull();
        assertThat(resolver.toUrl("")).isNull();
        assertThat(resolver.toUrl("   ")).isNull();
    }

    @Test
    @DisplayName("没配 base-url 且没有 OSS 配置时退化成相对路径（只作兜底）")
    void missingBaseUrl() {
        FileUrlResolver resolver = resolver(null);

        assertThat(resolver.toUrl(KEY)).isEqualTo("/" + KEY);
    }

    @Test
    @DisplayName("没配 base-url 时按 endpoint + bucket 推导 OSS 公共读域名（Bucket 开了公共读就能直接看图）")
    void derivesOssPublicDomain() {
        FileUrlResolver resolver = resolver(null, oss("https://oss-cn-guangzhou.aliyuncs.com", "blake-tikcookie"));

        assertThat(resolver.toUrl(KEY))
                .isEqualTo("https://blake-tikcookie.oss-cn-guangzhou.aliyuncs.com/" + KEY);
    }

    @Test
    @DisplayName("推导时忽略 endpoint 的路径与结尾斜杠；http 端点保持 http")
    void derivesFromMessyEndpoint() {
        FileUrlResolver resolver = resolver(null, oss("http://oss-cn-guangzhou.aliyuncs.com/", "demo"));

        assertThat(resolver.toUrl(KEY)).isEqualTo("http://demo.oss-cn-guangzhou.aliyuncs.com/" + KEY);
    }

    @Test
    @DisplayName("endpoint 已经是虚拟主机风格（主机名里带 bucket）时不再重复拼 bucket")
    void doesNotDuplicateBucket() {
        FileUrlResolver resolver = resolver(null, oss("https://demo.oss-cn-guangzhou.aliyuncs.com", "demo"));

        assertThat(resolver.toUrl(KEY)).isEqualTo("https://demo.oss-cn-guangzhou.aliyuncs.com/" + KEY);
    }

    @Test
    @DisplayName("配了 base-url 时以配置为准，不再推导（既能填 OSS 域名，也能填后端代理读图接口）")
    void configuredBaseUrlWins() {
        FileUrlResolver oss = resolver("https://img.example.com", oss("https://oss-cn-guangzhou.aliyuncs.com", "demo"));
        FileUrlResolver proxy = resolver("http://127.0.0.1:8080/api/v1/images",
                oss("https://oss-cn-guangzhou.aliyuncs.com", "demo"));

        assertThat(oss.toUrl(KEY)).isEqualTo("https://img.example.com/" + KEY);
        assertThat(proxy.toUrl(KEY)).isEqualTo("http://127.0.0.1:8080/api/v1/images/" + KEY);
    }

    @Test
    @DisplayName("OSS 配置只有一半（缺 bucket 或缺 endpoint）时不推导，退化成相对路径")
    void partialOssConfigFallsBack() {
        assertThat(resolver(null, oss("https://oss-cn-guangzhou.aliyuncs.com", null)).toUrl(KEY))
                .isEqualTo("/" + KEY);
        assertThat(resolver(null, oss("  ", "demo")).toUrl(KEY)).isEqualTo("/" + KEY);
    }

    @Test
    @DisplayName("数组转换保持原顺序，null 数组返回空列表")
    void listOrder() {
        FileUrlResolver resolver = resolver("https://img.example.com");
        List<String> keys = Arrays.asList(KEY, null, "group1/M00/00/00/ffffffffffffffffffffffffffffffff.png");

        List<String> urls = resolver.toUrls(keys);

        assertThat(urls).hasSize(3);
        assertThat(urls.get(0)).isEqualTo("https://img.example.com/" + KEY);
        assertThat(urls.get(1)).isNull();
        assertThat(urls.get(2)).isEqualTo("https://img.example.com/group1/M00/00/00/ffffffffffffffffffffffffffffffff.png");
        assertThat(resolver.toUrls(null)).isEmpty();
    }

    @Test
    @DisplayName("ObjectKey 生成后能被转换器直接拼成 URL（约定闭环，且不会出现双斜杠）")
    void generatedKeyIsResolvable() {
        FileUrlResolver resolver = resolver("https://api.example.com/api/v1/images");
        String generated = ObjectKeys.generate("png");

        assertThat(ObjectKeys.isValid(generated)).isTrue();
        assertThat(generated).startsWith("group1/M00/00/00/").doesNotContain("//");
        assertThat(resolver.toUrl(generated)).isEqualTo("https://api.example.com/api/v1/images/" + generated);
    }

    @Test
    @DisplayName("校验失败时抛 400 / 40000，提示引导前端先调上传接口")
    void requireValidRejectsExternalUrl() {
        assertThatThrownBy(() -> ObjectKeys.requireValid("https://evil.example.com/a.jpg"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(40000))
                .hasMessageContaining("图片地址不合法");
    }

    private FileUrlResolver resolver(String baseUrl) {
        return resolver(baseUrl, null);
    }

    private FileUrlResolver resolver(String baseUrl, DssProperties.OssProperties oss) {
        DssProperties properties = new DssProperties();
        properties.getFile().setBaseUrl(baseUrl);
        if (oss != null) {
            properties.getFile().setOss(oss);
        }
        return new FileUrlResolver(properties);
    }

    private DssProperties.OssProperties oss(String endpoint, String bucket) {
        DssProperties.OssProperties oss = new DssProperties.OssProperties();
        oss.setEndpoint(endpoint);
        oss.setBucket(bucket);
        return oss;
    }
}
