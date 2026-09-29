package com.dss.order.job;

import com.dss.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时兜底任务：每 5 分钟把已过 payDeadline 仍是 UNPAID 的订单按超时取消，防止延迟消息丢失。
 * 只有 dss.job.enabled=true 时才注册（骨架期关闭）。多副本下靠 Redis 锁 + 条件更新只处理一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dss.job", name = "enabled", havingValue = "true")
public class OrderTimeoutFallbackJob {

    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${dss.job.timeout-fallback-interval:PT5M}")
    public void run() {
        int count = orderService.cancelOverdueUnpaidOrders();
        log.info("超时兜底任务：取消了 {} 个订单", count);
    }
}
