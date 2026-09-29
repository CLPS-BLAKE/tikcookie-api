package com.dss.file.controller;

import com.dss.common.config.OpenApiConfig;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.result.Result;
import com.dss.file.model.vo.UploadVO;
import com.dss.file.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传：两个入口，登录用户上传头像，内部接口上传店铺图和商品图。接口文档 5.2.1、5.8.6。
 */
@Tag(name = "文件")
@RestController
@RequiredArgsConstructor
public class FileController {

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

    private UploadVO toVO(String fileId) {
        return new UploadVO(fileId, fileUrlResolver.toUrl(fileId));
    }
}
