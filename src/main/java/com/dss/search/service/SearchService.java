package com.dss.search.service;

import com.dss.common.mq.SearchSyncMessage;
import com.dss.common.result.PageResult;
import com.dss.search.model.dto.ProductSearchQuery;
import com.dss.search.model.dto.ShopSearchQuery;
import com.dss.search.model.vo.ProductHitVO;
import com.dss.search.model.vo.ShopHitVO;

/**
 * 只读搜索。ES 查询失败抛 107001；MySQL → ES 同步由 Logstash 负责，不使用 RabbitMQ。
 * 商品详情和剩余库存始终从 MySQL 查，ES 里的数据只用于搜索，不作为最终值。
 */
public interface SearchService {

    /**
     * 商品搜索（索引 dss_product）：
     * <ul>
     *     <li>过滤：status = ON_SHELF；category → shopCategory；type；</li>
     *     <li>keyword 不为空时，multi_match 查 name、contentsText、shopName；</li>
     *     <li>排序：default = _score 再 soldCount 倒序（keyword 为空时就是 soldCount 倒序）；
     *     sales = soldCount 倒序；price_asc / price_desc = price。</li>
     * </ul>
     */
    PageResult<ProductHitVO> searchProducts(ProductSearchQuery query);

    /**
     * 店铺搜索（索引 dss_shop）：keyword 查 name、address；category 过滤；
     * keyword 为空时按 createdAt 倒序（即按分类浏览）。
     */
    PageResult<ShopHitVO> searchShops(ShopSearchQuery query);

    /**
     * 历史兼容签名：Logstash 模式禁止调用，明确抛 UnsupportedOperationException，不写 ES。
     */
    void sync(SearchSyncMessage message);

    /**
     * 历史维护签名：Logstash 模式禁止删除索引，明确抛 UnsupportedOperationException。
     * 误开启动重建时失败并提示关闭 DSS_SEARCH_REBUILD_ON_STARTUP，由部署方按 Logstash 流程维护。
     */
    void rebuildAll();
}
