package com.dss.shop.service;

import com.dss.shop.model.dto.ShopSaveDTO;
import com.dss.shop.model.entity.Shop;
import com.dss.shop.model.vo.CategoryVO;
import com.dss.shop.model.vo.ShopVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 店铺与分类。规则见需求文档 6.2。后半部分方法供 product / order / favorite / search 模块调用。
 */
public interface ShopService {

    /**
     * 分类列表：直接把 ShopCategory 枚举转成 [{code, name}]，按枚举声明顺序。
     */
    List<CategoryVO> listCategories();

    /**
     * 店铺详情；不存在：102001。
     */
    ShopVO getShop(Long shopId);

    /**
     * 内部：新建店铺。ID 用 RedisIdGenerator（biz=shop），写 createTime / updateTime；
     * 成功后调 SearchSyncPublisher.publishShopChanged。
     */
    ShopVO createShop(ShopSaveDTO dto);

    /**
     * 内部：修改店铺（整体覆盖）；不存在：102001。成功后发 shop.upsert。
     * 店铺改名或改分类时，搜索模块会同步更新该店所有商品文档里的店铺名和分类。
     */
    ShopVO updateShop(Long shopId, ShopSaveDTO dto);

    // ---------- 供其他模块调用 ----------

    /**
     * 按 ID 取店铺实体；不存在：102001。
     */
    Shop getRequiredShop(Long shopId);

    /**
     * 批量取店铺（收藏列表、搜索同步用）；不存在的 ID 不出现在结果里。
     */
    Map<Long, Shop> getShopsByIds(Collection<Long> shopIds);
}
