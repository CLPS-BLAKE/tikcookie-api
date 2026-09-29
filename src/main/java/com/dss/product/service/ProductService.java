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
 * 商品。规则见需求文档 4.1、中间件配置 3.3。后半部分方法供 order / favorite / search 包调用。
 * 剩余库存以 MySQL products.stock 为准；商品有改动时，都要等 MySQL 提交后再发 product.changed。
 */
public interface ProductService {

    /**
     * 店铺的上架商品：店铺不存在 102001；只含 ON_SHELF，按 createdAt 倒序，不分页。
     */
    List<ProductCardVO> listShopProducts(Long shopId);

    /**
     * 商品详情：不存在 103001，已下架 103002；带店铺名称和地址；
     * FLASH 的 remainingStock 取 products.stock。
     */
    ProductVO getProduct(Long productId);

    /**
     * 首页商品流：只含 ON_SHELF；latest 按 createdAt 倒序，sales 按 soldCount 倒序；分页（selectPage）。
     */
    PageResult<ProductCardVO> listFeed(ProductFeedQuery query);

    /**
     * 抢购专区：type=FLASH、ON_SHELF、flashEndTime 晚于当前时间，按 flashStartTime 升序；
     * ongoing = 当前时间 ≥ flashStartTime；remainingStock 取 products.stock。
     */
    List<FlashProductVO> listFlash();

    /**
     * 内部：新建商品。
     * <ul>
     *     <li>店铺必须存在（102001）；</li>
     *     <li>FLASH 必须有 stock、flashStartTime、flashEndTime（103005），结束晚于开始（103006），limitPerUser 不传默认 1；</li>
     *     <li>NORMAL 忽略这 4 个抢购字段，存 NULL，不记库存；</li>
     *     <li>ID 由 MySQL 自增，status=ON_SHELF，soldCount=0；</li>
     *     <li>提交后发 product.changed。</li>
     * </ul>
     */
    ProductVO createProduct(ProductSaveDTO dto);

    /**
     * 内部：修改商品（整体覆盖）。
     * <ul>
     *     <li>不存在 103001；类型不同 103003；shopId 不同 103004；</li>
     *     <li>FLASH 且当前时间 ≥ 原 flashStartTime 时，stock / limitPerUser / 时间窗有任何变化：103007；</li>
     *     <li>提交后发 product.changed。</li>
     * </ul>
     */
    ProductVO updateProduct(Long productId, ProductSaveDTO dto);

    /**
     * 内部：上下架；不存在 103001。提交后发 product.changed。
     */
    void changeStatus(Long productId, ProductStatus status);

    // ---------- 供其他包调用 ----------

    /**
     * 按 ID 取商品实体（任何状态）；不存在 103001。
     */
    Product getRequiredProduct(Long productId);

    /**
     * 批量取商品（收藏列表、搜索同步用）；不存在的 ID 不出现在结果里。
     */
    Map<Long, Product> getProductsByIds(Collection<Long> productIds);

    /**
     * 在调用方的事务里锁定商品行（ProductMapper.selectByIdForUpdate）；不存在 103001。
     * 抢购下单、取消抢购单用，必须在 @Transactional 方法里调用。
     */
    Product lockProduct(Long productId);

    /**
     * 已售数 +delta（支付 +1，退款 −1），条件更新，不会减成负数；在订单事务里调用，提交后发 product.changed。
     */
    void changeSoldCount(Long productId, int delta);

    /**
     * 抢购下单时扣库存 1（条件 stock > 0），在订单事务里、lockProduct 之后调用。
     *
     * @return false 表示已抢光，调用方返回 103010
     */
    boolean decreaseFlashStock(Long productId);

    /**
     * 取消待支付的抢购订单时回补库存 1，在订单事务里、lockProduct 之后调用。
     */
    void increaseFlashStock(Long productId);
}
