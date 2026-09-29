package com.dss.order.job;

import com.dss.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 到期退款任务：每分钟把 expiresAt 已过仍是 UNUSED 的订单改为 REFUNDED（EXPIRED）；"去使用"接口自己也会拒绝过期订单。
 * 只有 dss.job.enabled=true 时才注册（骨架期关闭）。单实例运行，不加分布式锁，靠条件更新保证不重复处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dss.job", name = "enabled", havingValue = "true")
public class VoucherExpireJob {

    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${dss.job.voucher-expire-interval:PT1M}")
    public void run() {
        int count = orderService.refundExpiredVouchers();
        log.info("到期退款任务：退了 {} 个订单", count);
    }
}
