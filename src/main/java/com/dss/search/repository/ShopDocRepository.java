package com.dss.search.repository;

import com.dss.search.model.entity.ShopDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 索引 dss_shop 的简单读写；用法同 ProductDocRepository。
 */
public interface ShopDocRepository extends ElasticsearchRepository<ShopDoc, String> {
}
