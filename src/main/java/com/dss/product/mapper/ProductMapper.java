package com.dss.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dss.product.model.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * products 表。普通查询和分页用 BaseMapper + LambdaQueryWrapper，用到的索引：
 * 店铺商品 idx_products_shop_status_created，首页 idx_products_status_created / idx_products_status_sold，
 * 抢购专区 idx_products_type_status_start。
 * <p>
 * 下面几个方法用自定义 SQL（行锁、库存和已售数的条件更新，中间件配置 3.3）。
 * <p>
 * 这几个 SQL 自己写 updated_at：自定义语句不经过 MybatisPlusConfig 的 MetaObjectHandler。
 * 只读的行锁方法要加 @ResultMap("mybatis-plus_Product")，否则 contents / use_rules 两个 JSON 列读不出。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 在当前事务里锁定商品行：SELECT * FROM products WHERE id = #{id} FOR UPDATE。
     * 抢购下单、取消抢购单都先锁它；订单的支付、取消、退款为了和抢购下单保持同一把加锁顺序也先锁它。
     * 必须在 @Transactional 方法里调用，否则 FOR UPDATE 随语句结束立即释放。
     */
    @Select("SELECT * FROM products WHERE id = #{id} FOR UPDATE")
    @ResultMap("mybatis-plus_Product")
    Product selectByIdForUpdate(@Param("id") Long id);

    /**
     * 扣抢购库存 1：UPDATE products SET stock = stock - 1, updated_at = NOW(3) WHERE id = #{id} AND stock > 0。
     * 条件在 SQL 里，扣库存和判断"还有没有货"是一条语句，不存在两个请求都读到同一份库存的窗口。
     *
     * @return 更新行数，0 表示已抢光
     */
    @Update("UPDATE products SET stock = stock - 1, updated_at = NOW(3) WHERE id = #{id} AND stock > 0")
    int decreaseStock(@Param("id") Long id);

    /**
     * 回补抢购库存 1：UPDATE products SET stock = stock + 1, updated_at = NOW(3) WHERE id = #{id} AND stock IS NOT NULL。
     */
    @Update("UPDATE products SET stock = stock + 1, updated_at = NOW(3) WHERE id = #{id} AND stock IS NOT NULL")
    int increaseStock(@Param("id") Long id);

    /**
     * 已售数加减：UPDATE products SET sold_count = sold_count + #{delta}, updated_at = NOW(3)
     * WHERE id = #{id} AND sold_count + #{delta} >= 0。
     * 条件保证不会把已售数减成负数。
     */
    @Update("UPDATE products SET sold_count = sold_count + #{delta}, updated_at = NOW(3) "
            + "WHERE id = #{id} AND sold_count + #{delta} >= 0")
    int changeSoldCount(@Param("id") Long id, @Param("delta") int delta);
}
