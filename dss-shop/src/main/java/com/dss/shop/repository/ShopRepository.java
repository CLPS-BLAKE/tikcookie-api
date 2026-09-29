package com.dss.shop.repository;

import com.dss.shop.model.entity.Shop;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * shops 集合。索引 idx_category_createTime 由 docs/中间件配置.md 4.7 的建结构脚本创建。
 */
public interface ShopRepository extends MongoRepository<Shop, Long> {
}
