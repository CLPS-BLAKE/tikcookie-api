package com.dss.file.controller;

import com.dss.common.config.OpenApiConfig;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.result.Result;
import com.dss.file.model.vo.UploadVO;
import com.dss.file.service.FileStorageService;
import com.dss.file.service.StoredImage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件上传与读取：上传有两个入口（登录用户上传头像、内部口令上传店铺图和商品图），
 * 读取是公开的后端代理接口，把 OSS 里的图片转发给浏览器。
 * Bucket 已开公共读，返回给前端的 url 默认直连 OSS 域名（见 {@link FileUrlResolver}），
 * 代理读图接口作为备用路径保留：Bucket 若收紧为私有读，把 dss.file.base-url 指向它即可，前端无需改动。
 * 接口文档 5.2.1、5.8.6；读图见 docs/图片上传与搜索消息联调说明.md。
 */
@Tag(name = "文件")
@RestController
@RequiredArgsConstructor
public class FileController {

    /**
     * 公开读图的路径前缀。Bucket 私有读时 dss.file.base-url 要指向它（如 https://demo.example.com/api/v1/images），
     * 返回给前端的 url 才会正好落在这个接口上；Bucket 公共读（当前）时 url 直连 OSS 域名，这里作为备用路径。
     */
    public static final String IMAGE_READ_PATH = "/api/v1/images";

    /** 公开读图的完整映射：{*fileId} 把 ObjectKey 里的 "/" 一起吃进来。 */
    public static final String IMAGE_READ_MAPPING = IMAGE_READ_PATH + "/{*fileId}";

    private final FileStorageService fileStorageService;
    private final FileUrlResolver fileUrlResolver;

    @Operation(summary = "上传图片（登录用户，用于头像）", description = "multipart/form-data，字段 file；只接受 jpg/png/webp，最大 5MB")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping(value = "/api/v1/files/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadVO> uploadImage(@RequestPart("file") MultipartFile file) {
        return Result.ok(toVO(fileStorageService.uploadImage(file)));
    }

    @Operation(summary = "上传图片（内部，用于店铺图和商品图）", description = "参数和返回值同 /api/v1/files/images")
    @PostMapping(value = "/api/v1/internal/files/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UploadVO> uploadImageInternal(@RequestPart("file") MultipartFile file) {
        return Result.ok(toVO(fileStorageService.uploadImage(file)));
    }

    /**
     * 读取图片（公开，不用 token）：Bucket 已公共读，前端默认直连 OSS 域名；这个接口作为备用路径，
     * 用后端凭据取流再写给浏览器，Bucket 收紧为私有读时把 dss.file.base-url 指过来即可。
     * 路径就是 fileId 本身，因此 base-url 填后端地址时前端拿到的 url 直接可用。
     * <p>
     * fileId 格式不合法或对象不存在都返回 404 / 40400。
     */
    @Operation(summary = "读取图片（公开，备用代理路径）",
            description = "fileId 就是上传接口返回的 fileId；Bucket 公共读时前端直连 OSS，本接口供私有读场景使用")
    @GetMapping(IMAGE_READ_MAPPING)
    public void readImage(@PathVariable("fileId") String fileId,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        String objectKey = stripLeadingSlash(fileId);
        try (StoredImage image = fileStorageService.loadImage(objectKey)) {
            String etag = image.etag();
            if (etag != null && etag.equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
                response.setStatus(HttpStatus.NOT_MODIFIED.value());
                return;
            }
            response.setContentType(image.contentType());
            response.setContentLengthLong(image.contentLength());
            if (etag != null) {
                response.setHeader(HttpHeaders.ETAG, etag);
            }
            // ObjectKey 是一次性随机名，同一地址的内容永远不会变，可以放心长缓存
            response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
            image.content().transferTo(response.getOutputStream());
        }
    }

    private UploadVO toVO(String fileId) {
        return new UploadVO(fileId, fileUrlResolver.toUrl(fileId));
    }

    /** {*fileId} 捕获到的值带前导 "/"（也可能带多个），统一去掉再当成 ObjectKey 用。 */
    private String stripLeadingSlash(String path) {
        String result = path;
        while (result != null && result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }
}
