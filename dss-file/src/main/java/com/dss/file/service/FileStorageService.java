package com.dss.file.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象：目前是 FastDFS 实现，以后换 MinIO / OSS 只需要换实现类，业务代码不用动。
 * 库里只存 fileId（如 group1/M00/00/00/xxx.jpg），完整 URL 由 FileUrlResolver 拼。
 */
public interface FileStorageService {

    /**
     * 上传一张图片，返回 fileId。
     * <ul>
     *     <li>空文件：106001</li>
     *     <li>Content-Type 不在 dss.file.allowed-types（默认 jpg/png/webp）里：106002</li>
     *     <li>超过 dss.file.max-size（默认 5MB）：106003</li>
     *     <li>存储失败：106004</li>
     * </ul>
     */
    String uploadImage(MultipartFile file);

    /**
     * 删除文件（实现阶段按需使用；目前没有接口调用它）。
     */
    void delete(String fileId);
}
