package com.dss.order.job;

import com.dss.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时取消任务：每分钟把已过 payDeadline 仍是 UNPAID 的订单按超时取消（和用户取消走同一个事务方法）。
 * 这是唯一的超时机制，不再有 MQ 延迟消息；支付接口自己也会当场拒绝过期订单。
 * 只有 dss.job.enabled=true 时才注册（骨架期关闭）。单实例运行，不加分布式锁，靠条件更新保证不重复处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dss.job", name = "enabled", havingValue = "true")
public class OrderTimeoutJob {

    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${dss.job.order-timeout-interval:PT1M}")
    public void run() {
        int count = orderService.cancelOverdueUnpaidOrders();
        log.info("超时取消任务：取消了 {} 个订单", count);
    }
}
