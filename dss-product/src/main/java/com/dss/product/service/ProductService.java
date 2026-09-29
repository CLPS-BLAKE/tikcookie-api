package com.dss.product.service;

import com.dss.common.result.PageResult;
import com.dss.product.model.dto.ProductFeedQuery;
import com.dss.product.model.dto.ProductSaveDTO;
import com.dss.product.model.entity.Product;
import com.dss.product.model.enums.ProductStatus;
import com.dss.product.model.vo.FlashProductVO;
import com.dss.product.model.vo.ProductCardVO;
import com.dss.product.model.vo.ProductVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 商品。规则见需求文档 6.3。后半部分方法供 order / favorite / search 模块调用。
 */
public interface ProductService {

    /**
     * 店铺的上架商品：店铺不存在 102001；只含 ON_SHELF，按 createTime 倒序，不分页。
     */
    List<ProductCardVO> listShopProducts(Long shopId);

    /**
     * 商品详情：不存在 103001，已下架 103002；带店铺名称和地址；
     * FLASH 的 remainingStock 读 Redis dss:flash:stock:{id}。
     */
    ProductVO getProduct(Long productId);

    /**
     * 首页商品流：只含 ON_SHELF；latest 按 createTime 倒序，sales 按 soldCount 倒序；分页。
     */
    PageResult<ProductCardVO> listFeed(ProductFeedQuery query);

    /**
     * 抢购专区：type=FLASH、ON_SHELF、flashEndTime 晚于当前时间，按 flashStartTime 升序；
     * ongoing = 当前时间 ≥ flashStartTime；remainingStock 读 Redis。
     */
    List<FlashProductVO> listFlash();

    /**
     * 内部：新建商品。
     * <ul>
     *     <li>店铺必须存在（102001）；</li>
     *     <li>FLASH 必须有 stock、flashStartTime、flashEndTime（103005），结束晚于开始（103006），limitPerUser 不传默认 1；</li>
     *     <li>NORMAL 忽略这 4 个抢购字段；</li>
     *     <li>ID 用 RedisIdGenerator（biz=product），status=ON_SHELF，soldCount=0；</li>
     *     <li>FLASH：SET dss:flash:stock:{id} = stock，DEL dss:flash:bought:{id}；</li>
     *     <li>成功后发 product.upsert。</li>
     * </ul>
     */
    ProductVO createProduct(ProductSaveDTO dto);

    /**
     * 内部：修改商品（整体覆盖）。
     * <ul>
     *     <li>不存在 103001；类型不同 103003；shopId 不同 103004；</li>
     *     <li>FLASH 且当前时间 ≥ 原 flashStartTime 时，stock / limitPerUser / 时间窗有任何变化：103007；</li>
     *     <li>开抢前修改库存时，同时重写 Redis 库存；</li>
     *     <li>成功后发 product.upsert。</li>
     * </ul>
     */
    ProductVO updateProduct(Long productId, ProductSaveDTO dto);

    /**
     * 内部：上下架；不存在 103001。成功后发 product.upsert。
     */
    void changeStatus(Long productId, ProductStatus status);

    // ---------- 供其他模块调用 ----------

    /**
     * 按 ID 取商品实体（任何状态）；不存在 103001。
     */
    Product getRequiredProduct(Long productId);

    /**
     * 批量取商品（收藏列表、搜索同步用）；不存在的 ID 不出现在结果里。
     */
    Map<Long, Product> getProductsByIds(Collection<Long> productIds);

    /**
     * 已售数 +delta（支付 +1，退款 −1），用 $inc 原子更新；变化后发 product.upsert。
     */
    void changeSoldCount(Long productId, int delta);

    /**
     * 抢购落单时扣 Mongo 库存 1（条件 stock > 0 的原子更新），在订单事务里调用。
     *
     * @return false 表示 Mongo 库存已经是 0（Redis 与 Mongo 不一致，需要人工处理）
     */
    boolean decreaseFlashStock(Long productId);

    /**
     * 取消待支付的抢购订单时，回补 Mongo 库存 1。Redis 库存和已购数由订单模块回补。
     */
    void increaseFlashStock(Long productId);
}
