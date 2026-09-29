package com.dss.common.file;

import com.dss.common.config.DssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * fileId → 完整 URL（已实现）。放在 common 里，是因为 user / shop / product 等模块都要拼图片地址，
 * 而依赖规则不允许它们依赖 dss-file。
 */
@Component
@RequiredArgsConstructor
public class FileUrlResolver {

    private final DssProperties properties;

    /**
     * @param fileId 如 group1/M00/00/00/xxx.jpg
     * @return 基础地址 + fileId；fileId 为空时返回 null；基础地址为空时返回以 / 开头的站点相对地址
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
