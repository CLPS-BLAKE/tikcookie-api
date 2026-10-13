package com.dss.order.job;

import com.dss.order.service.OrderService;
import com.dss.common.config.DssProperties;
import com.dss.flash.service.FlashRuntime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时取消任务：每分钟把已过 payDeadline 仍是 UNPAID 的订单按超时取消（和用户取消走同一个事务方法）。
 * MQ 超时通知的数据库扫描兜底；支付接口锁后拒绝过期订单。播种未完成时不扫描。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dss.job", name = "enabled", havingValue = "true")
public class OrderTimeoutJob {

    private final OrderService orderService;
    private final FlashRuntime flashRuntime;
    private final DssProperties properties;

    @Scheduled(fixedDelayString = "${dss.job.order-timeout-interval:PT1M}")
    public void run() {
        if (properties.getFlash().isEnabled() && !flashRuntime.isReady()) return;
        int count = orderService.cancelOverdueUnpaidOrders();
        log.info("超时取消任务：取消了 {} 个订单", count);
    }
}
