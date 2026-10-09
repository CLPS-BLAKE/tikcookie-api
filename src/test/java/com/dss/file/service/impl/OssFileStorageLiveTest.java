package com.dss.file.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.dss.common.config.DssProperties;
import com.dss.common.exception.BizException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.file.ObjectKeys;
import com.dss.common.file.StoredImage;
import com.dss.file.config.OssConfig;
import com.dss.file.model.enums.FileErrorCode;
import com.dss.file.model.enums.ImageType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 OSS 联调（需要 tikcookie-api/.env 里的 DSS_OSS_* 配置；没有 .env 时自动跳过）。
 * 验证：真实上传 → 对象确实存在 → 内容类型正确 → 预签名 URL 可下载 → 后端 loadImage 取回同样字节 → 删除。
 * 另外确认 Bucket 是公共读：上传后返回给前端的 url 用匿名 GET 就能取回原图（游客直连看图），
 * 后端代理读图接口仍然可用，作为 Bucket 收紧为私有读时的备用路径。
 * <p>
 * 其中 {@link #realImageUploadAndPublicView()} 是给联调用的端到端冒烟用例：用仓库里的真实图片
 * （src/main/resources/imgs/img1.jpg）走完整链路，"上传能不能成功、上传后能不能看到"一次跑完，
 * 并把可直接在浏览器打开的 url 打到日志里（加 -Ddss.live.keep=true 会让对象留在 Bucket 供人工查看）。
 */
@EnabledIfEnvironmentVariable(named = "DSS_OSS_LIVE_TEST", matches = "(?i)true")
class OssFileStorageLiveTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==");

    /** 冒烟用例用的真实图片：优先 classpath（src/main/resources 会被打进 target/classes），其次按源码路径读。 */
    private static final String[] SAMPLE_IMAGE_CANDIDATES = {
            "imgs/img1.jpg", "imgs/img1.jpeg", "imgs/img1.png", "imgs/img1.webp"
    };

    private static Properties env;
    private static OSS ossClient;
    private static OssFileStorage storage;
    private static DssProperties properties;
    private static String bucket;

    @BeforeAll
    static void setUp() {
        env = loadEnv();
        Assumptions.assumeTrue(env != null && !env.getProperty("DSS_OSS_ENDPOINT", "").isBlank(),
                "没有 tikcookie-api/.env 或未配置 OSS，跳过真实 OSS 联调");

        properties = new DssProperties();
        properties.getFile().getOss().setEndpoint(env.getProperty("DSS_OSS_ENDPOINT"));
        properties.getFile().getOss().setBucket(env.getProperty("DSS_OSS_BUCKET"));
        properties.getFile().getOss().setRegion(env.getProperty("DSS_OSS_REGION"));
        properties.getFile().getOss().setAccessKeyId(env.getProperty("DSS_OSS_ACCESS_KEY_ID", ""));
        properties.getFile().getOss().setAccessKeySecret(env.getProperty("DSS_OSS_ACCESS_KEY_SECRET", ""));
        properties.getFile().setBaseUrl(env.getProperty("DSS_FILE_BASE_URL", ""));

        try {
            ossClient = new OssConfig().ossClient(properties);
        } catch (RuntimeException e) {
            Assumptions.assumeTrue(false, "OSS 客户端初始化失败，跳过：" + e.getMessage());
        }
        storage = new OssFileStorage(properties, ossClient);
        bucket = properties.getFile().getOss().getBucket();
    }

    @AfterAll
    static void tearDown() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    @Test
    @DisplayName("真实上传：对象落在 Bucket 里，内容类型正确，预签名 URL 能下载回来")
    void uploadThenReadBack() throws Exception {
        String objectKey = storage.uploadImage(new MockMultipartFile("file", "live-check.png", "image/png", PNG));

        assertThat(ObjectKeys.isValid(objectKey)).isTrue();
        assertThat(ossClient.doesObjectExist(bucket, objectKey)).isTrue();

        ObjectMetadata metadata = ossClient.getObjectMetadata(bucket, objectKey);
        assertThat(metadata.getContentType()).isEqualTo("image/png");
        assertThat(metadata.getContentLength()).isEqualTo(PNG.length);

        // 预签名 URL 用后端凭据读取，不依赖 Bucket 的匿名读权限
        URL signed = ossClient.generatePresignedUrl(bucket, objectKey,
                new java.util.Date(System.currentTimeMillis() + 120_000));
        HttpURLConnection connection = (HttpURLConnection) signed.openConnection();
        connection.setRequestMethod("GET");
        try {
            assertThat(connection.getResponseCode()).isEqualTo(200);
            assertThat(connection.getHeaderField("Content-Type")).isEqualTo("image/png");
            try (InputStream in = connection.getInputStream()) {
                assertThat(in.readAllBytes()).isEqualTo(PNG);
            }
        } finally {
            connection.disconnect();
        }

        // 顺便验证真实删除
        storage.delete(objectKey);
        assertThat(ossClient.doesObjectExist(bucket, objectKey)).isFalse();
    }

    @Test
    @DisplayName("读图：loadImage 从真实 Bucket 取回的字节、Content-Type、长度与上传完全一致")
    void loadImageFromRealBucket() throws Exception {
        String objectKey = storage.uploadImage(new MockMultipartFile("file", "live-read.png", "image/png", PNG));
        try (StoredImage image = storage.loadImage(objectKey)) {
            assertThat(image.contentType()).isEqualTo("image/png");
            assertThat(image.contentLength()).isEqualTo(PNG.length);
            assertThat(image.content().readAllBytes()).isEqualTo(PNG);
            System.out.println("[INFO] 真实读图成功：key=" + objectKey + "，etag=" + image.etag());
        } finally {
            storage.delete(objectKey);
        }
    }

    @Test
    @DisplayName("读图：真实环境下不存在的对象返回 404 / 40400，不是 500")
    void loadImageMissingObjectReturns404() {
        String absent = ObjectKeys.generate("png");

        assertThatThrownBy(() -> storage.loadImage(absent))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(40400);
                    assertThat(((BizException) e).getErrorCode().getHttpStatus()).isEqualTo(404);
                });
    }

    @Test
    @DisplayName("冒烟：真实图片 imgs/img1.jpg 上传到 OSS，游客匿名打开返回的 url 就能看到这张图")
    void realImageUploadAndPublicView() throws Exception {
        SampleImage sample = sampleImage();
        System.out.println("[INFO] 测试图片：" + sample.source() + "（" + sample.type().getContentType()
                + "，" + sample.bytes().length + " 字节）");

        // 走和上传接口完全一样的服务方法（校验 + 生成 ObjectKey + 写 OSS）
        String objectKey = storage.uploadImage(
                new MockMultipartFile("file", sample.filename(), sample.type().getContentType(), sample.bytes()));
        String url = new FileUrlResolver(properties).toUrl(objectKey);
        System.out.println("[INFO] 上传成功：fileId=" + objectKey);
        System.out.println("[INFO] 浏览器可直接打开（游客/登录用户都能看到）：" + url);

        try {
            assertThat(ossClient.doesObjectExist(bucket, objectKey)).as("对象应真的落在 Bucket 里").isTrue();
            ObjectMetadata metadata = ossClient.getObjectMetadata(bucket, objectKey);
            assertThat(metadata.getContentType()).isEqualTo(sample.type().getContentType());
            assertThat(metadata.getContentLength()).isEqualTo(sample.bytes().length);

            // 1) 前端/游客视角：不带任何凭据直连 url，等价于 <img src>
            HttpURLConnection anon = (HttpURLConnection) URI.create(url).toURL().openConnection();
            anon.setRequestMethod("GET");
            anon.setConnectTimeout(10_000);
            anon.setReadTimeout(20_000);
            try {
                int code = anon.getResponseCode();
                assertThat(code)
                        .as("公共读 Bucket 应允许匿名访问：%s（403 AccessDenied 说明 Bucket 还是私有读）", url)
                        .isEqualTo(200);
                assertThat(anon.getHeaderField("Content-Type")).isEqualTo(sample.type().getContentType());
                try (InputStream in = anon.getInputStream()) {
                    assertThat(in.readAllBytes()).as("匿名读到的一定是上传的那张图").isEqualTo(sample.bytes());
                }
                System.out.println("[INFO] 匿名访问 " + url + " 返回 HTTP " + code + "，字节与上传一致");
            } finally {
                anon.disconnect();
            }

            // 2) 后端代理视角：Bucket 若收紧为私有读，改用 /api/v1/images/{fileId} 也能读回同一张图
            try (StoredImage image = storage.loadImage(objectKey)) {
                assertThat(image.contentType()).isEqualTo(sample.type().getContentType());
                assertThat(image.content().readAllBytes()).isEqualTo(sample.bytes());
                System.out.println("[INFO] 后端代理读图（loadImage）同样取回原图，备用路径可用");
            }
        } finally {
            if (keepUploadedObject()) {
                System.out.println("[INFO] 已按 -Ddss.live.keep=true 保留对象，可在浏览器打开：" + url);
            } else {
                storage.delete(objectKey);
                System.out.println("[INFO] 已清理测试对象（想留着自己看就加 -Ddss.live.keep=true）");
            }
        }
    }

    @Test
    @DisplayName("游客直连看图：上传后返回的 url 用匿名 GET 能取回同一张图（Bucket 公共读）")
    void uploadedImageIsPubliclyReadable() throws Exception {
        String objectKey = storage.uploadImage(new MockMultipartFile("file", "live-public.png", "image/png", PNG));
        try {
            assertThat(ossClient.doesObjectExist(bucket, objectKey)).isTrue();

            // 不带任何凭据的裸访问，等价于浏览器里的 <img src>（游客和登录用户都一样）
            String url = new FileUrlResolver(properties).toUrl(objectKey);
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            try {
                int code = connection.getResponseCode();
                assertThat(code)
                        .as("公共读 Bucket 应允许匿名访问：%s（403 说明 Bucket 还是私有读）", url)
                        .isEqualTo(200);
                assertThat(connection.getHeaderField("Content-Type")).isEqualTo("image/png");
                try (InputStream in = connection.getInputStream()) {
                    assertThat(in.readAllBytes()).isEqualTo(PNG);
                }
                System.out.println("[INFO] 匿名访问 " + url + " 返回 HTTP " + code + "（公共读符合预期）");
            } finally {
                connection.disconnect();
            }
        } finally {
            storage.delete(objectKey);
        }
    }

    @Test
    @DisplayName("真实环境下的错误码：不存在的对象删除不报 106004")
    void deletingMissingObjectIsHarmless() {
        String absent = ObjectKeys.generate("png");

        storage.delete(absent);
    }

    @Test
    @DisplayName("真实环境下的错误码：错误的凭据/端点会转成 106004，而不是把 SDK 异常抛给客户端")
    void wrongEndpointMapsTo106004() {
        DssProperties properties = new DssProperties();
        properties.getFile().getOss().setEndpoint("https://oss-cn-guangzhou.aliyuncs.com");
        properties.getFile().getOss().setBucket("bucket-that-does-not-exist-" + System.nanoTime());
        properties.getFile().getOss().setAccessKeyId(env.getProperty("DSS_OSS_ACCESS_KEY_ID", ""));
        properties.getFile().getOss().setAccessKeySecret(env.getProperty("DSS_OSS_ACCESS_KEY_SECRET", ""));
        OSS broken = new OSSClientBuilder().build(properties.getFile().getOss().getEndpoint(),
                properties.getFile().getOss().getAccessKeyId(), properties.getFile().getOss().getAccessKeySecret());
        try {
            OssFileStorage brokenStorage = new OssFileStorage(properties, broken);

            assertThatThrownBy(() -> brokenStorage.uploadImage(
                    new MockMultipartFile("file", "a.png", "image/png", PNG)))
                    .isInstanceOf(BizException.class)
                    .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(FileErrorCode.FILE_UPLOAD_FAILED));
        } finally {
            broken.shutdown();
        }
    }

    /** 解析 tikcookie-api/.env（properties 形式），没有就返回 null。 */
    private static Properties loadEnv() {
        Path envFile = Paths.get(".env");
        if (!Files.isReadable(envFile)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(envFile)) {
            properties.load(in);
        } catch (Exception e) {
            return null;
        }
        return properties;
    }

    /** 仓库里的真实测试图片（imgs/img1.jpg 等），找不到就让用例跳过。 */
    private static SampleImage sampleImage() throws Exception {
        for (String candidate : SAMPLE_IMAGE_CANDIDATES) {
            try (InputStream in = OssFileStorageLiveTest.class.getClassLoader().getResourceAsStream(candidate)) {
                if (in != null) {
                    return sample(candidate, in.readAllBytes());
                }
            }
            // 直接跑单测时 classpath 里可能还没有 target/classes，退化成读源码目录
            Path local = Paths.get("src/main/resources", candidate);
            if (Files.isReadable(local)) {
                return sample(candidate, Files.readAllBytes(local));
            }
        }
        Assumptions.assumeTrue(false, "找不到测试图片 " + String.join(" / ", SAMPLE_IMAGE_CANDIDATES) + "，跳过");
        return null;
    }

    private static SampleImage sample(String resourcePath, byte[] bytes) {
        String filename = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        ImageType type = ImageType.ofExtension(
                filename.substring(filename.lastIndexOf('.') + 1));
        // 后缀认不出来，或文件真实内容与后缀不一致：用例应当直接失败，而不是拿错类型去上传
        assertThat(type).as("测试图片后缀必须是 jpg/png/webp：%s", resourcePath).isNotNull();
        assertThat(ImageType.sniff(bytes))
                .as("测试图片 %s 的真实内容（魔数）与后缀不一致，上传接口会按 106002 拒绝", resourcePath)
                .isEqualTo(type);
        return new SampleImage(resourcePath, filename, type, bytes);
    }

    /** 想在上传后自己用浏览器看，就跑 -Ddss.live.keep=true（或用环境变量 DSS_LIVE_KEEP=true）。 */
    private static boolean keepUploadedObject() {
        String value = System.getProperty("dss.live.keep",
                System.getenv().getOrDefault("DSS_LIVE_KEEP", "false"));
        return Boolean.parseBoolean(value);
    }

    private record SampleImage(String source, String filename, ImageType type, byte[] bytes) {
    }
}
