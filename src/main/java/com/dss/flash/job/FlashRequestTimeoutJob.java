package com.dss.flash.job;

import com.dss.flash.service.FlashOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="dss.flash",name="enabled",havingValue="true")
public class FlashRequestTimeoutJob {
    private final FlashOrderService orders;
    @Scheduled(fixedDelayString="${dss.flash.request-timeout-interval:PT1S}")
    public void run() {
        try { orders.expireRequests(); }
        catch (RuntimeException e) { log.warn("Flash timeout scan unavailable; reservations retained for reconciliation"); }
    }
}
