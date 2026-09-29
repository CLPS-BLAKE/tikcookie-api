package com.dss.search.repository;

import com.dss.search.model.entity.ProductDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 索引 dss_product。必须继承 ElasticsearchRepository（而不是通用的 CrudRepository）：
 * 工程里同时有 Mongo 和 ES 两套 Spring Data，要靠接口类型区分归属。
 */
public interface ProductDocRepository extends ElasticsearchRepository<ProductDoc, String> {
}
