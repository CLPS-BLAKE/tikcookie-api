package com.dss.file.controller;

import com.dss.common.config.DssProperties;
import com.dss.common.error.CommonErrorCode;
import com.dss.common.exception.BizException;
import com.dss.common.exception.GlobalExceptionHandler;
import com.dss.common.file.FileUrlResolver;
import com.dss.file.model.enums.FileErrorCode;
import com.dss.file.service.FileStorageService;
import com.dss.file.service.StoredImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 三个文件接口的响应契约：
 * 上传（登录 / 内部）成功 data 是 {fileId, url}，文件类业务错误走 HTTP 200 + 106xxx，缺 file 部分走 HTTP 400 + 40000；
 * 读图是公开接口，直接把图片字节写给浏览器（200 + Content-Type + 长缓存，命中 ETag 返回 304）。
 */
class FileControllerTest {

    private static final String API_BASE = "https://api.example.com/api/v1/images";
    private static final String KEY = "group1/M00/00/00/0123456789abcdef0123456789abcdef.jpg";
    private static final String CONSUMER_PATH = "/api/v1/files/images";
    private static final String INTERNAL_PATH = "/api/v1/internal/files/images";
    private static final String READ_PATH = "/api/v1/images/" + KEY;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    private FileStorageService fileStorageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        fileStorageService = mock(FileStorageService.class);
        DssProperties properties = new DssProperties();
        properties.getFile().setBaseUrl(API_BASE);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FileController(fileStorageService, new FileUrlResolver(properties)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ---------- 上传 ----------

    @Test
    @DisplayName("C 端上传（头像）：code=0，data 是 fileId + 拼好的 url（指向后端读图接口）")
    void consumerUploadSuccess() throws Exception {
        when(fileStorageService.uploadImage(any())).thenReturn(KEY);

        mockMvc.perform(multipart(CONSUMER_PATH).file(image()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("ok"))
                .andExpect(jsonPath("$.data.fileId").value(KEY))
                .andExpect(jsonPath("$.data.url").value(API_BASE + "/" + KEY));
    }

    @Test
    @DisplayName("内部上传（店铺图 / 商品图）：和 C 端共用同一套上传逻辑")
    void internalUploadSuccess() throws Exception {
        when(fileStorageService.uploadImage(any())).thenReturn(KEY);

        mockMvc.perform(multipart(INTERNAL_PATH).file(image()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileId").value(KEY))
                .andExpect(jsonPath("$.data.url").value(API_BASE + "/" + KEY));
    }

    @Test
    @DisplayName("文件类型不对：HTTP 200 + 106002（业务码不用 4xx）")
    void typeNotAllowedReturnsHttp200() throws Exception {
        when(fileStorageService.uploadImage(any()))
                .thenThrow(new BizException(FileErrorCode.FILE_TYPE_NOT_ALLOWED));

        mockMvc.perform(multipart(CONSUMER_PATH).file(image()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(106002))
                .andExpect(jsonPath("$.msg").value("只支持 jpg、png、webp 图片"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("空文件：HTTP 200 + 106001")
    void emptyFileReturns106001() throws Exception {
        when(fileStorageService.uploadImage(any())).thenThrow(new BizException(FileErrorCode.FILE_EMPTY));

        mockMvc.perform(multipart(INTERNAL_PATH).file(image()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(106001));
    }

    @Test
    @DisplayName("上游 OSS 失败：HTTP 200 + 106004")
    void uploadFailureReturns106004() throws Exception {
        when(fileStorageService.uploadImage(any())).thenThrow(new BizException(FileErrorCode.FILE_UPLOAD_FAILED));

        mockMvc.perform(multipart(CONSUMER_PATH).file(image()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(106004));
    }

    @Test
    @DisplayName("没带 file 部分：HTTP 400 + 40000（不是 106xxx）")
    void missingFilePartReturns400() throws Exception {
        mockMvc.perform(multipart(CONSUMER_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @DisplayName("响应是 application/json（前端不用关心 charset 细节）")
    void responseIsJson() throws Exception {
        when(fileStorageService.uploadImage(any())).thenReturn(KEY);

        mockMvc.perform(multipart(CONSUMER_PATH).file(image()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ---------- 读图（后端代理，公开） ----------

    @Test
    @DisplayName("读图：把图片字节原样写给浏览器，带 Content-Type / Content-Length / 长缓存 / ETag")
    void readImageReturnsBytes() throws Exception {
        when(fileStorageService.loadImage(KEY)).thenReturn(stored());

        mockMvc.perform(get(READ_PATH))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, PNG.length))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-1\""))
                .andExpect(content().bytes(PNG));

        verify(fileStorageService).loadImage(KEY);
    }

    @Test
    @DisplayName("读图：If-None-Match 命中时返回 304，不再传图片内容")
    void readImageReturns304WhenEtagMatches() throws Exception {
        when(fileStorageService.loadImage(KEY)).thenReturn(stored());

        MvcResult result = mockMvc.perform(get(READ_PATH).header(HttpHeaders.IF_NONE_MATCH, "\"etag-1\""))
                .andExpect(status().isNotModified())
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("读图：fileId 不合法或对象不存在 → HTTP 404 + 40400")
    void readImageNotFound() throws Exception {
        when(fileStorageService.loadImage(anyString()))
                .thenThrow(new BizException(CommonErrorCode.NOT_FOUND, "图片不存在"));

        mockMvc.perform(get("/api/v1/images/group1/M00/00/00/not-a-uuid.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.msg").value("图片不存在"));
    }

    @Test
    @DisplayName("读图路径常量与上传路径分开：/api/v1/images 读，/api/v1/files/images 写")
    void readPathConstants() {
        assertThat(FileController.IMAGE_READ_PATH).isEqualTo("/api/v1/images");
        assertThat(FileController.IMAGE_READ_MAPPING).isEqualTo("/api/v1/images/{*fileId}");
    }

    private StoredImage stored() {
        return new StoredImage(new ByteArrayInputStream(PNG), "image/png", PNG.length, "\"etag-1\"");
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "头像.jpg", "image/jpeg",
                "fake-jpeg-bytes".getBytes(StandardCharsets.UTF_8));
    }
}
