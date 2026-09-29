package com.dss.common.file;

import com.dss.common.config.DssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * fileId → 完整 URL（已实现）：url = dss.file.base-url + "/" + fileId（中间件配置第 6 节）。
 * fileId 是 OSS ObjectKey。放在 common 里，是因为 user / shop / product 等包都要拼图片地址，
 * 而按包的依赖约定，它们不能调用 file 包。
 */
@Component
@RequiredArgsConstructor
public class FileUrlResolver {

    private final DssProperties properties;

    /**
     * @param fileId OSS ObjectKey，如 group1/M00/00/00/xxx.jpg（只是路径形式沿用接口示例，不部署 FastDFS）
     * @return 基础地址 + "/" + fileId；fileId 为空时返回 null
     */
    public String toUrl(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return null;
        }
        String base = properties.getFile().getBaseUrl();
        if (base == null) {
            base = "";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = fileId.startsWith("/") ? fileId.substring(1) : fileId;
        return base + "/" + path;
    }

    public List<String> toUrls(List<String> fileIds) {
        if (fileIds == null) {
            return List.of();
        }
        return fileIds.stream().map(this::toUrl).toList();
    }
}
