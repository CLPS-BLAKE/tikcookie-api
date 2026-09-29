package com.dss.order.repository;

import com.dss.order.model.entity.Order;
import com.dss.order.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * orders 集合。5 个索引由 docs/中间件配置.md 4.7 的建结构脚本创建，每个查询方法后面注明了它用到的索引。
 * 状态迁移用 MongoTemplate.findAndModify 做条件更新，不走这里。
 */
public interface OrderRepository extends MongoRepository<Order, Long> {

    /** 核销（uk_voucherCode）。 */
    Optional<Order> findByVoucherCode(String voucherCode);

    /** 我的订单：全部（idx_userId_createTime）。 */
    Page<Order> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    /** 我的订单：按状态（idx_userId_status_createTime）。 */
    Page<Order> findByUserIdAndStatusOrderByCreateTimeDesc(Long userId, OrderStatus status, Pageable pageable);

    /** 超时兜底任务，每次最多 100 条（idx_status_payDeadline）。 */
    List<Order> findTop100ByStatusAndPayDeadlineBefore(OrderStatus status, LocalDateTime time);

    /** 过期退款任务，每次最多 100 条（idx_status_expireTime）。 */
    List<Order> findTop100ByStatusAndExpireTimeBefore(OrderStatus status, LocalDateTime time);
}
