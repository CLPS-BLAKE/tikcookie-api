package com.dss.file.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.dss.common.config.DssProperties;
import com.dss.common.error.CommonErrorCode;
import com.dss.common.exception.BizException;
import com.dss.common.file.FileStorageService;
import com.dss.common.file.ObjectKeys;
import com.dss.common.file.StoredImage;
import com.dss.file.model.enums.FileErrorCode;
import com.dss.file.model.enums.ImageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 阿里云 OSS 实现。连接参数在 dss.file.oss.*（endpoint、bucket、region、AccessKey），见中间件配置第 2、6 节。
 * ObjectKey 统一由 {@link ObjectKeys#generate(String)} 生成（group1/M00/00/00/{uuid}.{后缀}），不用原始文件名；
 * 校验顺序：非空 → 大小 → 后缀与魔数（106002），全部通过后才写 OSS。
 * 上传成功只代表图片已保存到 OSS，用户头像、商品图片等数据库写入仍由对应业务模块负责。
 * Bucket 已开公共读：上传后的对象可由浏览器直连 OSS 域名展示（url 由 FileUrlResolver 拼）；
 * {@link #loadImage(String)} 是保留下来的后端代理读图路径，供 Bucket 收紧为私有读时使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssFileStorage implements FileStorageService {

    /** 读多少字节做魔数校验：WEBP 需要前 12 个字节。 */
    private static final int HEAD_BYTES = 12;

    private final DssProperties properties;
    private final OSS ossClient;

    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(FileErrorCode.FILE_EMPTY);
        }
        long maxBytes = properties.getFile().getMaxSize().toBytes();
        if (file.getSize() > maxBytes) {
            // 先比大小再读内容：超限的文件不读、不传
            log.warn("图片超过业务上限：size={}，max={}", file.getSize(), maxBytes);
            throw new BizException(FileErrorCode.FILE_TOO_LARGE);
        }

        ImageType type = resolveType(file);
        String objectKey = ObjectKeys.generate(type.getExtension());
        upload(file, objectKey, type);
        return objectKey;
    }

    @Override
    public void delete(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            log.warn("删除文件时 fileId 为空，忽略");
            return;
        }
        ObjectKeys.requireValid(fileId);
        String bucket = bucket();
        try {
            ossClient.deleteObject(bucket, fileId);
            log.info("图片已删除：bucket={}，objectKey={}", bucket, fileId);
        } catch (Exception e) {
            log.error("图片删除失败：bucket={}，objectKey={}", bucket, fileId, e);
            throw new BizException(CommonErrorCode.SYSTEM_ERROR, "文件删除失败");
        }
    }

    /**
     * 读图（后端代理路径）：用后端凭据取流，由控制器写给浏览器；Bucket 公共读时前端一般直连 OSS，不走这里。
     * 一次 getObject 就能同时拿到内容和元数据（Content-Type、长度、ETag），不额外发 HEAD 请求。
     */
    @Override
    public StoredImage loadImage(String fileId) {
        if (!ObjectKeys.isValid(fileId)) {
            // 公开读接口：格式不合法等同于"没有这张图"，不把校验规则暴露给匿名请求
            log.warn("读图请求的 fileId 不合法，按 404 处理");
            throw new BizException(CommonErrorCode.NOT_FOUND, "图片不存在");
        }
        String bucket = bucket();
        try {
            OSSObject object = ossClient.getObject(bucket, fileId);
            ObjectMetadata metadata = object.getObjectMetadata();
            String contentType = metadata.getContentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : metadata.getContentType();
            return new StoredImage(object.getObjectContent(), contentType, metadata.getContentLength(),
                    metadata.getETag());
        } catch (Exception e) {
            // 对象不存在、Bucket 权限或配置不对，都按 404 返回；真实原因（含 OSS 错误码）只进日志
            log.error("读取图片失败：bucket={}，objectKey={}", bucket, fileId, e);
            throw new BizException(CommonErrorCode.NOT_FOUND, "图片不存在");
        }
    }

    /**
     * 后缀和真实内容都必须是同一种允许的图片类型，且该类型在 dss.file.allowed-types 里。
     * 只看 Content-Type 会被伪装文件绕过，所以以魔数为准、后缀复核。
     */
    private ImageType resolveType(MultipartFile file) {
        ImageType byExtension = ImageType.ofExtension(extensionOf(file.getOriginalFilename()));
        if (byExtension == null) {
            throw new BizException(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        ImageType byContent = ImageType.sniff(readHead(file));
        if (byContent == null || byContent != byExtension) {
            log.warn("图片内容与后缀不一致：originalFilename={}，声明 Content-Type={}，识别结果={}",
                    file.getOriginalFilename(), file.getContentType(), byContent);
            throw new BizException(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        if (!properties.getFile().getAllowedTypes().contains(byContent.getContentType())) {
            log.warn("图片类型不在 dss.file.allowed-types 里：type={}，allowed={}",
                    byContent.getContentType(), properties.getFile().getAllowedTypes());
            throw new BizException(FileErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        return byContent;
    }

    /**
     * 上传图片
     * @param file
     * @param objectKey
     * @param type
     */
    private void upload(MultipartFile file, String objectKey, ImageType type) {
        String bucket = bucket();
        ObjectMetadata metadata = new ObjectMetadata();
        // 用识别出来的类型，不用客户端声明的 Content-Type
        metadata.setContentType(type.getContentType());
        metadata.setContentLength(file.getSize());
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            ossClient.putObject(bucket, objectKey, in, metadata);
            log.info("图片已上传：bucket={}，objectKey={}，size={}，contentType={}",
                    bucket, objectKey, file.getSize(), type.getContentType());
        } catch (IOException e) {
            log.error("读取上传文件失败：objectKey={}", objectKey, e);
            throw new BizException(FileErrorCode.FILE_UPLOAD_FAILED);
        } catch (Exception e) {
            log.error("图片上传 OSS 失败：bucket={}，objectKey={}，size={}", bucket, objectKey, file.getSize(), e);
            throw new BizException(FileErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private byte[] readHead(MultipartFile file) {
        byte[] head = new byte[HEAD_BYTES];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(head, 0, HEAD_BYTES);
            if (read < HEAD_BYTES) {
                byte[] actual = new byte[Math.max(read, 0)];
                System.arraycopy(head, 0, actual, 0, Math.max(read, 0));
                return actual;
            }
            return head;
        } catch (IOException e) {
            log.error("读取上传文件头失败：originalFilename={}", file.getOriginalFilename(), e);
            throw new BizException(FileErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    /** 取最后一个点之后的部分；没有点或点是首字符时返回 null。 */
    private String extensionOf(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return null;
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return null;
        }
        return originalFilename.substring(dot + 1);
    }

    private String bucket() {
        return properties.getFile().getOss().getBucket();
    }
}
