package com.dss.search.service;

import com.dss.common.mq.SearchSyncMessage;
import com.dss.common.result.PageResult;
import com.dss.search.model.dto.ProductSearchQuery;
import com.dss.search.model.dto.ShopSearchQuery;
import com.dss.search.model.vo.ProductHitVO;
import com.dss.search.model.vo.ShopHitVO;

/**
 * 搜索与同步。规则见需求文档 6.5。ES 不可用时抛 107001。
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
     * keyword 为空时按 createTime 倒序（即按分类浏览）。
     */
    PageResult<ShopHitVO> searchShops(ShopSearchQuery query);

    /**
     * 搜索同步（SearchSyncConsumer 调用）：按 docType 从 Mongo 读最新数据，整条覆盖写入 ES；
     * SHOP 时还要把该店所有商品文档里的 shopName、shopCategory 一起更新（按 shopId 批量更新）。
     * 商品下架不删文档，只更新 status。
     */
    void sync(SearchSyncMessage message);
}
