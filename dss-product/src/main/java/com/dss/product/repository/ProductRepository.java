package com.dss.product.repository;

import com.dss.product.model.entity.Product;
import com.dss.product.model.enums.ProductStatus;
import com.dss.product.model.enums.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * products 集合。4 个索引由 docs/中间件配置.md 4.7 的建结构脚本创建，每个查询方法后面注明了它用到的索引。
 */
public interface ProductRepository extends MongoRepository<Product, Long> {

    /** 店铺的上架商品（idx_shopId_status）。 */
    List<Product> findByShopIdAndStatusOrderByCreateTimeDesc(Long shopId, ProductStatus status);

    /** 首页商品流，排序由 Pageable 决定（idx_status_createTime / idx_status_soldCount）。 */
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    /** 抢购专区：未结束的抢购商品（idx_type_status_flashStartTime）。 */
    List<Product> findByTypeAndStatusAndFlashEndTimeAfterOrderByFlashStartTimeAsc(
            ProductType type, ProductStatus status, LocalDateTime now);
}
