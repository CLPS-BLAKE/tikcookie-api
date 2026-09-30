package com.dss.common.file;

import com.dss.common.config.DssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * fileId → 完整 URL（已实现）：url = 图片公共前缀 + "/" + fileId（中间件配置第 6 节）。
 * fileId 是 OSS ObjectKey。
 * 而按包的依赖约定，它们不能调用 file 包。
 * 公共前缀按下面的顺序取（见 {@link #publicBase()}）：
 *     配了 dss.file.base-url 就用它：Bucket 公共读时填 OSS 公共域名
 *         （或自己的 CNAME 域名），也可以填后端代理读图接口 {@code /api/v1/images}；
 *     没配时按 dss.file.oss.endpoint + bucket 推导出 OSS 默认公共域名
 *         https://{bucket}.{endpoint-host}，这样开了公共读就能直接用，不必多配一个变量；
 *     连 OSS 配置都没有时退化成相对路径（只作兜底，正常部署不会走到）。
 */
@Component
@RequiredArgsConstructor
public class FileUrlResolver {

    private final DssProperties properties;

    /**
     * @param fileId OSS ObjectKey，如 img/3f2b9c4e7a1d4f0e8b6c5a2d1e0f9a8b.jpg（由 ObjectKeys 生成，存进库里）
     * @return 公共前缀 + "/" + fileId；fileId 为空时返回 null
     */
    public String toUrl(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return null;
        }
        String path = fileId.startsWith("/") ? fileId.substring(1) : fileId;
        return publicBase() + "/" + path;
    }

    /** 图片公共前缀，末尾一定没有 "/"（配置里多写几个也算），拼接时不会出现 // 或 ///。 */
    private String publicBase() {
        String configured = properties.getFile().getBaseUrl();
        String base = StringUtils.hasText(configured) ? configured.trim() : ossPublicBase();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * 从 OSS 配置推导公共读域名：https://{bucket}.{endpoint-host}。
     * endpoint 已经带了 bucket（虚拟主机风格）时不重复拼；取不到 endpoint / bucket 时返回空串。
     */
    private String ossPublicBase() {
        DssProperties.OssProperties oss = properties.getFile().getOss();
        if (oss == null || !StringUtils.hasText(oss.getEndpoint()) || !StringUtils.hasText(oss.getBucket())) {
            return "";
        }
        String bucket = oss.getBucket().trim();
        String raw = oss.getEndpoint().trim();
        String scheme = "https";
        int schemeEnd = raw.indexOf("://");
        if (schemeEnd >= 0) {
            scheme = raw.substring(0, schemeEnd);
            raw = raw.substring(schemeEnd + 3);
        }
        // endpoint 后面若跟了路径（或结尾的 /），只取主机名部分
        int slash = raw.indexOf('/');
        String host = slash >= 0 ? raw.substring(0, slash) : raw;
        if (host.isEmpty()) {
            return "";
        }
        // 形如 https://my-bucket.oss-cn-guangzhou.aliyuncs.com 的 endpoint：bucket 已经在主机名里了
        return host.startsWith(bucket + ".") ? scheme + "://" + host : scheme + "://" + bucket + "." + host;
    }

    public List<String> toUrls(List<String> fileIds) {
        if (fileIds == null) {
            return List.of();
        }
        return fileIds.stream().map(this::toUrl).toList();
    }
}
