package com.dss.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置（已实现）：
 * - 分页插件（MySQL），BaseMapper.selectPage 靠它生成 LIMIT 和 COUNT；
 * - 自动填充实体上标了 fill 的 createdAt / updatedAt，建表脚本里的默认值只作兜底。
 * Mapper 接口标 @Mapper，由 starter 自动扫描；JDBC 事务管理器由 Spring Boot 按 DataSource 自动配置。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 时间取 JVM 默认时区（启动类里固定为 Asia/Shanghai），和 JDBC 的 connectionTimeZone 一致。
     * 只对按实体写库的方法生效；用 UpdateWrapper 做条件更新时，要自己 set updatedAt。
     */
    @Bean
    public MetaObjectHandler auditTimeMetaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
