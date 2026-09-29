package com.dss.file.service.impl;

import com.dss.common.config.DssProperties;
import com.dss.common.exception.NotImplementedException;
import com.dss.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * FastDFS 实现（骨架期是桩）。
 * 连接参数在 dss.file.fastdfs.*；Java 客户端还没选定（tobato 停在 2020 年且面向 Spring Boot 2，
 * 官方 fastdfs-client-java 没发到 Maven Central），见 docs/中间件配置.md 5.5。
 */
@Service
@RequiredArgsConstructor
public class FastDfsFileStorage implements FileStorageService {

    private final DssProperties properties;

    @Override
    public String uploadImage(MultipartFile file) {
        throw new NotImplementedException();
    }

    @Override
    public void delete(String fileId) {
        throw new NotImplementedException();
    }
}
