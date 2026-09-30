package com.dss.file.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import com.dss.common.config.DssProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * OSS 客户端（单例）：整个应用共用一个 {@link OSS}，随容器关闭调用 shutdown 释放连接池。
 * 凭据两种来源：
 *     配了 dss.file.oss.access-key-id / access-key-secret（实际值只放受控环境文件）时直接用它们；
 *     没配时交给 SDK 读环境变量 OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET，
 *         适合 ECS 实例角色等由环境注入凭据的部署方式。
 * endpoint 和 bucket 没有默认值，缺失时应用启动就会失败（宁可起不来也不连错 Bucket）。
 */
@Slf4j
@Configuration
public class OssConfig {

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(DssProperties properties) {
        DssProperties.OssProperties oss = properties.getFile().getOss();
        String endpoint = require(oss.getEndpoint(), "DSS_OSS_ENDPOINT（dss.file.oss.endpoint）");
        String bucket = require(oss.getBucket(), "DSS_OSS_BUCKET（dss.file.oss.bucket）");

        OSSClientBuilder builder = new OSSClientBuilder();
        OSS client;
        boolean withStaticKey = StringUtils.hasText(oss.getAccessKeyId())
                && StringUtils.hasText(oss.getAccessKeySecret());
        if (withStaticKey) {
            client = builder.build(endpoint, oss.getAccessKeyId(), oss.getAccessKeySecret());
        } else {
            log.info("未配置 dss.file.oss.access-key-id/secret，改用环境变量凭据（OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET）");
            client = builder.build(endpoint, new EnvironmentVariableCredentialsProvider());
        }
        log.info("OSS 客户端已初始化：endpoint={}，bucket={}，region={}，凭据来源={}",
                endpoint, bucket, oss.getRegion(), withStaticKey ? "配置项" : "环境变量");
        return client;
    }

    private String require(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("缺少 OSS 配置 " + name);
        }
        return value.trim();
    }
}
