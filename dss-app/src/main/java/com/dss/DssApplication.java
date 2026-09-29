package com.dss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * 抖省省仿制练手 · 启动类。包路径 com.dss，能扫到所有模块的 com.dss.* 组件。
 * Mongo 和 ES 两套 Spring Data 按仓库接口类型（MongoRepository / ElasticsearchRepository）自动区分。
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class DssApplication {

    public static void main(String[] args) {
        // 统一东八区：Mongo 存 LocalDateTime 时按 JVM 默认时区换算，各环境必须一致
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(DssApplication.class, args);
    }
}
