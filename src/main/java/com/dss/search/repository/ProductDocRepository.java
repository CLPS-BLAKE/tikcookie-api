package com.dss.search.repository;

import com.dss.search.model.entity.ProductDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 索引 dss_product 的简单读写（按 ID 覆盖写入、批量写入）；带过滤和排序的搜索在 SearchServiceImpl 里用 ElasticsearchOperations。
 * MySQL 走 MyBatis-Plus，Spring Data 的仓库只有 ES 这两个。
 */
public interface ProductDocRepository extends ElasticsearchRepository<ProductDoc, String> {
}
