package com.dss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * 抖省省仿制练手 · 启动类。包路径 com.dss，能扫到 com.dss.* 下所有包的组件。
 * 数据访问：MySQL 走 MyBatis-Plus（各包 mapper 下的 @Mapper 接口），ES 走 Spring Data Elasticsearch（com.dss.search.repository）。
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class DssApplication {

    public static void main(String[] args) {
        // 统一东八区：LocalDateTime 和 MySQL DATETIME 之间不做时区换算，JVM、JDBC 的 connectionTimeZone、MySQL 三者必须一致
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(DssApplication.class, args);
    }
}
