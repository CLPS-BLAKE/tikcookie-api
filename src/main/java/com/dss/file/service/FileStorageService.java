package com.dss.file.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储：阿里云 OSS 实现（OssFileStorage），规则见需求文档 4.3、中间件配置第 6 节。
 * fileId 就是 OSS ObjectKey（如 group1/M00/00/00/{uuid}.jpg，只是沿用接口示例的路径形式，不部署 FastDFS）；
 * 库里只存 fileId，返回给前端的 url 由 FileUrlResolver 拼成 dss.file.base-url + "/" + fileId。
 */
public interface FileStorageService {

    /**
     * 上传一张图片，返回 fileId（ObjectKey）。
     * <ul>
     *     <li>空文件：106001</li>
     *     <li>扩展名或实际内容类型不是 jpg/png/webp（dss.file.allowed-types）：106002</li>
     *     <li>超过 dss.file.max-size（默认 5MB）：106003</li>
     *     <li>上传 OSS 失败：106004</li>
     * </ul>
     * ObjectKey 由后端随机生成，不用用户传的文件名；OSS 凭据只在后端，前端拿不到。
     */
    String uploadImage(MultipartFile file);

    /**
     * 删除文件（实现阶段按需使用；目前没有接口调用它）。
     * 上传成功但数据库写入失败时留下的孤立文件，教学版允许存在，由部署人员定期清理。
     */
    void delete(String fileId);
}
