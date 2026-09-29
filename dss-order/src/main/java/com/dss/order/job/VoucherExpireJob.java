package com.dss.order.job;

import com.dss.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 过期自动退款任务：每 10 分钟把 expireTime 已过仍是 UNUSED 的订单改为 REFUNDED（EXPIRED）。
 * 只有 dss.job.enabled=true 时才注册（骨架期关闭）。多副本下靠 Redis 锁 + 条件更新只处理一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dss.job", name = "enabled", havingValue = "true")
public class VoucherExpireJob {

    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${dss.job.voucher-expire-interval:PT10M}")
    public void run() {
        int count = orderService.refundExpiredVouchers();
        log.info("过期退款任务：退了 {} 个订单", count);
    }
}
