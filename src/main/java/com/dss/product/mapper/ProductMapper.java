package com.dss.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dss.product.model.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * products 表。普通查询和分页用 BaseMapper + LambdaQueryWrapper，用到的索引：
 * 店铺商品 idx_products_shop_status_created，首页 idx_products_status_created / idx_products_status_sold，
 * 抢购专区 idx_products_type_status_start。
 * <p>
 * 下面几个方法要自定义 SQL（行锁、库存和已售数的条件更新，中间件配置 3.3）。骨架期只有签名，
 * SQL 写在注释里，实现时用 @Select / @Update 补上。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 在当前事务里锁定商品行：SELECT * FROM products WHERE id = #{id} FOR UPDATE。
     * 抢购下单、取消抢购单都先锁它；要加 @ResultMap("mybatis-plus_Product") 才能读出 JSON 列。
     */
    Product selectByIdForUpdate(@Param("id") Long id);

    /**
     * 扣抢购库存 1：UPDATE products SET stock = stock - 1, updated_at = NOW(3) WHERE id = #{id} AND stock > 0。
     *
     * @return 更新行数，0 表示已抢光
     */
    int decreaseStock(@Param("id") Long id);

    /**
     * 回补抢购库存 1：UPDATE products SET stock = stock + 1, updated_at = NOW(3) WHERE id = #{id} AND stock IS NOT NULL。
     */
    int increaseStock(@Param("id") Long id);

    /**
     * 已售数加减：UPDATE products SET sold_count = sold_count + #{delta}, updated_at = NOW(3)
     * WHERE id = #{id} AND sold_count + #{delta} >= 0。
     */
    int changeSoldCount(@Param("id") Long id, @Param("delta") int delta);
}
