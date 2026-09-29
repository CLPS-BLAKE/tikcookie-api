package com.dss.search.repository;

import com.dss.search.model.entity.ShopDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 索引 dss_shop。必须继承 ElasticsearchRepository，理由同 ProductDocRepository。
 */
public interface ShopDocRepository extends ElasticsearchRepository<ShopDoc, String> {
}
