package com.dss.search.service;

import com.dss.common.mq.SearchSyncMessage;
import com.dss.common.result.PageResult;
import com.dss.search.model.dto.ProductSearchQuery;
import com.dss.search.model.dto.ShopSearchQuery;
import com.dss.search.model.vo.ProductHitVO;
import com.dss.search.model.vo.ShopHitVO;

/**
 * 搜索与同步。规则见需求文档 4.3、中间件配置 5.2。ES 查询失败抛 107001。
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
     * 搜索同步（SearchSyncConsumer 调用）：按消息里的 type 和 id 从 MySQL 读当前记录（ShopService / ProductService），
     * 整条覆盖写入 ES；SHOP 时还要刷新该店所有商品文档里的 shopName、shopCategory。
     * 商品下架不删文档，只更新 status。
     */
    void sync(SearchSyncMessage message);

    /**
     * 全量重建（SearchIndexRebuildRunner 调用，只给部署方用，不开放接口）：
     * 删掉 dss_shop、dss_product 两个索引，按 ShopDoc / ProductDoc 的注解重建 mapping，
     * 再从 MySQL 分批遍历全部店铺和商品写入。演示前、死信积压或 ES 重启后跑一次。
     */
    void rebuildAll();
}
