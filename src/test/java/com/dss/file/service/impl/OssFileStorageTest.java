package com.dss.file.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.dss.common.config.DssProperties;
import com.dss.common.exception.BizException;
import com.dss.common.file.ObjectKeys;
import com.dss.common.file.StoredImage;
import com.dss.file.model.enums.FileErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OSS 上传/读取实现：空文件 106001、类型不对 106002、超过 5MB 106003、上传失败 106004；
 * ObjectKey 由后端生成；类型以文件真实内容（魔数）为准，不信客户端的 Content-Type。
 * 读图（后端代理路径）走 loadImage：格式不合法或存储侧失败一律 404。
 * OSS 客户端用 mock，不连真实 Bucket（真实联调见 OssFileStorageLiveTest）。
 */
class OssFileStorageTest {

    private static final String BUCKET = "dss-demo-image";
    private static final String KEY = "group1/M00/00/00/0123456789abcdef0123456789abcdef.png";

    private static final byte[] PNG = concat(
            new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, new byte[]{0, 0, 0, 13});
    private static final byte[] JPEG = concat(
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, new byte[]{0, 16, 'J', 'F', 'I', 'F'});
    private static final byte[] WEBP = "RIFF....WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);

    private DssProperties properties;
    private OSS ossClient;
    private OssFileStorage storage;

    @BeforeEach
    void setUp() {
        properties = new DssProperties();
        properties.getFile().setMaxSize(DataSize.ofMegabytes(5));
        properties.getFile().getOss().setBucket(BUCKET);
        ossClient = mock(OSS.class);
        storage = new OssFileStorage(properties, ossClient);
    }

    // ---------- 校验 ----------

    @Test
    @DisplayName("空文件：106001，不碰 OSS")
    void emptyFile() {
        assertCode(FileErrorCode.FILE_EMPTY, () -> storage.uploadImage(file("a.png", new byte[0], 0)));

        verifyNoInteractions(ossClient);
    }

    @Test
    @DisplayName("file 为 null：106001")
    void nullFile() {
        assertCode(FileErrorCode.FILE_EMPTY, () -> storage.uploadImage(null));
    }

    @Test
    @DisplayName("超过 5MB：106003，且不读内容、不传 OSS")
    void tooLarge() {
        byte[] big = new byte[(int) DataSize.ofMegabytes(5).toBytes() + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        assertCode(FileErrorCode.FILE_TOO_LARGE, () -> storage.uploadImage(file("big.png", big, big.length)));

        verifyNoInteractions(ossClient);
    }

    @Test
    @DisplayName("刚好 5MB：允许（边界值）")
    void exactlyAtLimit() {
        byte[] exact = new byte[(int) DataSize.ofMegabytes(5).toBytes()];
        System.arraycopy(PNG, 0, exact, 0, PNG.length);

        assertThat(storage.uploadImage(file("exact.png", exact, exact.length))).isNotNull();
    }

    @Test
    @DisplayName("后缀不在白名单（gif）：106002")
    void unsupportedExtension() {
        assertCode(FileErrorCode.FILE_TYPE_NOT_ALLOWED, () -> storage.uploadImage(file("a.gif", PNG, PNG.length)));
    }

    @Test
    @DisplayName("没有后缀：106002")
    void noExtension() {
        assertCode(FileErrorCode.FILE_TYPE_NOT_ALLOWED, () -> storage.uploadImage(file("avatar", PNG, PNG.length)));
    }

    @Test
    @DisplayName("伪装图片（文本内容改名 .jpg）：106002，靠魔数识别")
    void fakeImage() {
        byte[] text = "this is not an image at all".getBytes(StandardCharsets.UTF_8);

        assertCode(FileErrorCode.FILE_TYPE_NOT_ALLOWED, () -> storage.uploadImage(file("fake.jpg", text, text.length)));

        verifyNoInteractions(ossClient);
    }

    @Test
    @DisplayName("内容与后缀不一致（PNG 内容叫 .jpg）：106002")
    void mismatchedExtension() {
        assertCode(FileErrorCode.FILE_TYPE_NOT_ALLOWED, () -> storage.uploadImage(file("a.jpg", PNG, PNG.length)));
    }

    @Test
    @DisplayName("空后缀（文件名以点结尾）：106002")
    void trailingDot() {
        assertCode(FileErrorCode.FILE_TYPE_NOT_ALLOWED, () -> storage.uploadImage(file("a.", PNG, PNG.length)));
    }

    @Test
    @DisplayName("声明 image/gif 但内容是被篡改的字节：106002")
    void spoofedContentTypeWithBrokenContent() {
        byte[] broken = concat(new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00}, new byte[9]);

        assertCode(FileErrorCode.FILE_TYPE_NOT_ALLOWED,
                () -> storage.uploadImage(file("a.jpg", "image/gif", broken, broken.length)));
    }

    @Test
    @DisplayName("allowed-types 不含该类型时也拒绝：106002（配置可以收紧）")
    void typeNotAllowedByConfig() {
        properties.getFile().setAllowedTypes(List.of("image/png"));

        assertCode(FileErrorCode.FILE_TYPE_NOT_ALLOWED, () -> storage.uploadImage(file("a.jpg", JPEG, JPEG.length)));
    }

    // ---------- 上传 ----------

    @Test
    @DisplayName("上传成功：ObjectKey 由后端生成（group1/M00/00/00/{uuid}.{后缀}，无双斜杠），内容类型用识别结果，大小写进元数据")
    void uploadSuccess() {
        byte[] content = concat(PNG, "payload".getBytes(StandardCharsets.UTF_8));

        String fileId = storage.uploadImage(file("头像.png", "text/plain", content, content.length));

        assertThat(fileId).matches("group1/M00/00/00/[0-9a-f]{32}\\.png");
        assertThat(fileId).doesNotContain("//");
        assertThat(ObjectKeys.isValid(fileId)).isTrue();

        ArgumentCaptor<ObjectMetadata> metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(ossClient).putObject(eq(BUCKET), eq(fileId), any(InputStream.class), metadata.capture());
        assertThat(metadata.getValue().getContentType()).isEqualTo("image/png");
        assertThat(metadata.getValue().getContentLength()).isEqualTo(content.length);
    }

    @Test
    @DisplayName("jpeg 后缀统一生成 .jpg（jpg 和 jpeg 都算 JPEG）")
    void jpegExtensionNormalized() {
        assertThat(storage.uploadImage(file("a.jpeg", JPEG, JPEG.length))).matches("group1/M00/00/00/[0-9a-f]{32}\\.jpg");
        assertThat(storage.uploadImage(file("a.jpg", JPEG, JPEG.length))).matches("group1/M00/00/00/[0-9a-f]{32}\\.jpg");
    }

    @Test
    @DisplayName("webp 通过（前 12 字节含 RIFF....WEBP）")
    void webpAccepted() {
        String fileId = storage.uploadImage(file("a.webp", WEBP, WEBP.length));

        assertThat(fileId).matches("group1/M00/00/00/[0-9a-f]{32}\\.webp");
        ArgumentCaptor<ObjectMetadata> metadata = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(ossClient).putObject(eq(BUCKET), eq(fileId), any(InputStream.class), metadata.capture());
        assertThat(metadata.getValue().getContentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("两次上传不会生成同一个 ObjectKey（不用原始文件名，避免覆盖）")
    void keysAreUnique() {
        String first = storage.uploadImage(file("同一个名字.png", PNG, PNG.length));
        String second = storage.uploadImage(file("同一个名字.png", PNG, PNG.length));

        assertThat(first).isNotEqualTo(second);
        assertThat(first).doesNotContain("同一个名字");
    }

    @Test
    @DisplayName("上传用的输入流会关闭")
    void streamIsClosed() {
        TrackingMultipartFile tracking = new TrackingMultipartFile(PNG);

        storage.uploadImage(tracking);

        assertThat(tracking.closed.get()).isTrue();
    }

    @Test
    @DisplayName("OSS 抛异常：106004，不把 SDK 的异常原文透给客户端")
    void uploadFailure() {
        doThrow(new OSSException("AccessDenied: bucket policy denies PutObject"))
                .when(ossClient).putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class));

        assertThatThrownBy(() -> storage.uploadImage(file("a.png", PNG, PNG.length)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException biz = (BizException) e;
                    assertThat(biz.getErrorCode().getCode()).isEqualTo(106004);
                    assertThat(biz.getErrorCode().getHttpStatus()).isEqualTo(200);
                    assertThat(biz.getMessage()).isEqualTo("文件上传失败");
                    assertThat(biz.getMessage()).doesNotContain("AccessDenied");
                });
    }

    // ---------- 读图（后端代理路径） ----------

    @Test
    @DisplayName("读图成功：一次 getObject 拿到字节流、Content-Type、长度和 ETag")
    void loadImageSuccess() throws Exception {
        // 注意：ossObject(...) 内部会打桩，必须先建好再放进 thenReturn，否则 Mockito 报 UnfinishedStubbing
        OSSObject object = ossObject("image/png", PNG.length, "\"etag-1\"", new ByteArrayInputStream(PNG));
        when(ossClient.getObject(BUCKET, KEY)).thenReturn(object);

        try (StoredImage image = storage.loadImage(KEY)) {
            assertThat(image.contentType()).isEqualTo("image/png");
            assertThat(image.contentLength()).isEqualTo(PNG.length);
            assertThat(image.etag()).isEqualTo("\"etag-1\"");
            assertThat(image.content().readAllBytes()).isEqualTo(PNG);
        }

        verify(ossClient).getObject(BUCKET, KEY);
    }

    @Test
    @DisplayName("读图：fileId 格式不合法（外部 URL、穿越、短文件名、空值）直接 404，不查 OSS")
    void loadImageRejectsInvalidKey() {
        for (String bad : new String[]{null, "", "   ", "../secret", "https://evil.example.com/a.jpg",
                "group1/M00/00/00/short.jpg", "img/0123456789abcdef0123456789abcdef.png"}) {
            assertThatThrownBy(() -> storage.loadImage(bad))
                    .as("非法 fileId: %s", bad)
                    .isInstanceOf(BizException.class)
                    .satisfies(e -> assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(40400));
        }

        verifyNoInteractions(ossClient);
    }

    @Test
    @DisplayName("读图：OSS 报错（对象不存在等）一律转 404，不把 SDK 错误信息透给匿名请求")
    void loadImageMapsOssFailureTo404() {
        doThrow(new OSSException("NoSuchKey: The specified key does not exist."))
                .when(ossClient).getObject(BUCKET, KEY);

        assertThatThrownBy(() -> storage.loadImage(KEY))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(40400);
                    assertThat(e.getMessage()).isEqualTo("图片不存在").doesNotContain("NoSuchKey");
                });
    }

    @Test
    @DisplayName("读图：OSS 没给 Content-Type 时退化成 application/octet-stream")
    void loadImageWithoutContentType() throws Exception {
        OSSObject object = ossObject(null, PNG.length, null, new ByteArrayInputStream(PNG));
        when(ossClient.getObject(BUCKET, KEY)).thenReturn(object);

        try (StoredImage image = storage.loadImage(KEY)) {
            assertThat(image.contentType()).isEqualTo("application/octet-stream");
            assertThat(image.etag()).isNull();
        }
    }

    @Test
    @DisplayName("读图：关闭 StoredImage 会关闭 OSS 响应流（否则连接泄漏）")
    void loadImageClosesStream() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream tracked = new FilterInputStream(new ByteArrayInputStream(PNG)) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
        OSSObject object = ossObject("image/png", PNG.length, null, tracked);
        when(ossClient.getObject(BUCKET, KEY)).thenReturn(object);

        storage.loadImage(KEY).close();

        assertThat(closed.get()).isTrue();
    }

    // ---------- 删除（按需实现，没有接口调用） ----------

    @Test
    @DisplayName("删除：fileId 为空时忽略，不调用 OSS")
    void deleteBlank() {
        storage.delete(null);
        storage.delete("  ");

        verifyNoInteractions(ossClient);
    }

    @Test
    @DisplayName("删除：非法 fileId 直接拒绝（400 / 40000），不去删别人的对象")
    void deleteInvalidFileId() {
        assertThatThrownBy(() -> storage.delete("../../other-bucket/key.jpg"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(40000));

        verify(ossClient, never()).deleteObject(anyString(), anyString());
    }

    @Test
    @DisplayName("删除：合法 ObjectKey 调 deleteObject")
    void deleteValidFileId() {
        String fileId = ObjectKeys.generate("png");

        storage.delete(fileId);

        verify(ossClient).deleteObject(BUCKET, fileId);
    }

    @Test
    @DisplayName("删除失败：500 / 50000，不是「文件上传失败」")
    void deleteFailure() {
        String fileId = ObjectKeys.generate("png");
        doThrow(new OSSException("NoSuchBucket")).when(ossClient).deleteObject(BUCKET, fileId);

        assertThatThrownBy(() -> storage.delete(fileId))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    assertThat(((BizException) e).getErrorCode().getCode()).isEqualTo(50000);
                    assertThat(((BizException) e).getMessage()).isEqualTo("文件删除失败");
                });
    }

    // ---------- 辅助 ----------

    private void assertCode(FileErrorCode expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatCode(callable).isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(expected));
    }

    /** 造一个 OSS 的 getObject 结果：内容 + 元数据。 */
    private OSSObject ossObject(String contentType, long contentLength, String etag, InputStream content) {
        ObjectMetadata metadata = mock(ObjectMetadata.class);
        when(metadata.getContentType()).thenReturn(contentType);
        when(metadata.getContentLength()).thenReturn(contentLength);
        when(metadata.getETag()).thenReturn(etag);
        OSSObject object = mock(OSSObject.class);
        when(object.getObjectMetadata()).thenReturn(metadata);
        when(object.getObjectContent()).thenReturn(content);
        return object;
    }

    private MultipartFile file(String originalFilename, byte[] content, int declaredSize) {
        return file(originalFilename, "image/png", content, declaredSize);
    }

    private MultipartFile file(String originalFilename, String contentType, byte[] content, int declaredSize) {
        return new SizeReportingMultipartFile("file", originalFilename, contentType, content, declaredSize);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /** 按声明的长度报告 size，用来构造"超过 5MB"的边界用例而不真的分配 10MB。 */
    private static class SizeReportingMultipartFile extends MockMultipartFile {

        private final long declaredSize;

        SizeReportingMultipartFile(String name, String originalFilename, String contentType, byte[] content, long declaredSize) {
            super(name, originalFilename, contentType, content);
            this.declaredSize = declaredSize;
        }

        @Override
        public long getSize() {
            return declaredSize;
        }
    }

    /** 记录输入流是否被关闭。 */
    private static class TrackingMultipartFile extends MockMultipartFile {

        private final AtomicBoolean closed = new AtomicBoolean();

        TrackingMultipartFile(byte[] content) {
            super("file", "a.png", "image/png", content);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            InputStream delegate = super.getInputStream();
            return new FilterInputStream(delegate) {
                @Override
                public void close() throws IOException {
                    closed.set(true);
                    super.close();
                }
            };
        }
    }
}
