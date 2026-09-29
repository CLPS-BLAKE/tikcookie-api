package com.dss.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * MongoDB 配置（已实现）：
 * - 不写 Spring Data 默认的 _class 字段；
 * - 声明事务管理器。抢购落单"插订单 + 扣库存"要用多文档事务，MongoDB 必须以副本集运行。
 * 索引不由应用创建（auto-index-creation=false），唯一真源是 docs/中间件配置.md 4.7 的建结构脚本。
 */
@Configuration
public class MongoConfig {

    /** 替换 Spring Boot 默认的转换器，只多做一件事：typeKey 设为 null，即不写 _class。 */
    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory factory,
                                                       MongoMappingContext context,
                                                       MongoCustomConversions conversions) {
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(factory);
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, context);
        converter.setCustomConversions(conversions);
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return converter;
    }

    /** Spring Boot 不会自动配置 Mongo 事务管理器，需要手动声明。 */
    @Bean
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
