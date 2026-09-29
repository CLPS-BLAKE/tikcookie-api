package com.dss.file.service.impl;

import com.dss.common.config.DssProperties;
import com.dss.common.exception.NotImplementedException;
import com.dss.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 阿里云 OSS 实现（骨架期是桩）。连接参数在 dss.file.oss.*（endpoint、bucket、region、AccessKey），见中间件配置第 2、6 节。
 * 实现时：用 aliyun-sdk-oss 的 OSSClientBuilder 建一个单例客户端（应用关闭时 shutdown），
 * 按 group1/M00/00/00/{uuid}.{后缀} 生成 ObjectKey，putObject 上传到演示 Bucket（公共读、禁止匿名写）。
 */
@Service
@RequiredArgsConstructor
public class OssFileStorage implements FileStorageService {

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
