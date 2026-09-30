package com.dss.file.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储：阿里云 OSS 实现（OssFileStorage），规则见需求文档 4.3、中间件配置第 6 节。
 * fileId 就是 OSS ObjectKey（如 group1/M00/00/00/{uuid}.jpg，由 ObjectKeys 统一生成）；
 * 库里只存 fileId，返回给前端的 url 由 FileUrlResolver 拼成 `图片公共前缀 + "/" + fileId`
 * （前缀取 dss.file.base-url，未配置时推导出 OSS 公共域名）。
 * <p>
 * Bucket 已开公共读：url 直连 OSS 域名，游客和登录用户都能看到图片。读图仍保留后端代理接口
 * （GET /api/v1/images/{fileId} → {@link #loadImage(String)}），供 Bucket 收紧为私有读时使用。
 */
public interface FileStorageService {

    /**
     * 上传一张图片，返回 fileId（ObjectKey）。
     * 空文件：106001
     *  扩展名或实际内容类型不是 jpg/png/webp（dss.file.allowed-types）：106002
     *  超过 dss.file.max-size（默认 5MB）：106003
     *  上传 OSS 失败：106004
     * ObjectKey 由后端随机生成，不用用户传的文件名；OSS 凭据只在后端，前端拿不到。
     */
    String uploadImage(MultipartFile file);

    /**
     * 删除文件（实现阶段按需使用；目前没有接口调用它）。
     * 上传成功但数据库写入失败时留下的孤立文件，教学版允许存在，由部署人员定期清理。
     */
    void delete(String fileId);

    /**
     * 读取一张图片的内容，供后端代理读图接口（GET /api/v1/images/{fileId}）写给浏览器。
     * 调用方必须关闭返回值（try-with-resources）。
     * <p>
     * fileId 格式不合法、对象不存在、Bucket 权限或配置有问题，一律抛 404 / 40400：
     * 读图是公开接口，不向匿名请求区分"没有这张图"和"存储侧出错"，真实原因只记服务端日志。
     */
    StoredImage loadImage(String fileId);
}
