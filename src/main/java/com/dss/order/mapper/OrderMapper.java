package com.dss.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dss.order.model.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * orders 表，全部用 BaseMapper + Lambda 条件构造器，用到的索引：
 * <ul>
 *     <li>我的订单：idx_orders_user_created / idx_orders_user_status_created，selectPage 分页；</li>
 *     <li>限购计数：idx_orders_product_user_status，selectCount（product_id、user_id，status ≠ CANCELLED）；</li>
 *     <li>定时任务：idx_orders_status_deadline / idx_orders_status_expires，每批 LIMIT 100；</li>
 *     <li>状态迁移：update(null, LambdaUpdateWrapper)，条件 id + status = 原状态，同时 set updatedAt；
 *     返回的更新行数不为 1 就当作状态已变（中间件配置 3.3）。</li>
 * </ul>
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
