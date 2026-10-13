package com.dss.flash.job;

import com.dss.flash.service.*;
import com.dss.product.mapper.ProductMapper;
import com.dss.product.model.entity.Product;
import com.dss.product.model.enums.ProductStatus;
import com.dss.shop.service.ShopService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;
import java.time.*;

@Slf4j
@Component
@ConditionalOnProperty(prefix="dss.flash",name="enabled",havingValue="true")
public class FlashLifecycleJob {
    private final FlashRuntime runtime;
    private final FlashCacheService cache;
    private final FlashRequestCoordinator coordinator;
    private final ProductMapper products;
    private final ShopService shops;
    private final TransactionTemplate tx;
    private final Clock clock;
    public FlashLifecycleJob(FlashRuntime runtime,FlashCacheService cache,FlashRequestCoordinator coordinator,
                             ProductMapper products,ShopService shops,PlatformTransactionManager manager,Clock clock) {
        this.runtime=runtime; this.cache=cache; this.coordinator=coordinator; this.products=products;
        this.shops=shops; this.tx=new TransactionTemplate(manager); this.clock=clock;
    }
    @Scheduled(fixedDelayString="${dss.flash.lifecycle-interval:PT1S}")
    public void run() {
        if (!runtime.isReady()) return;
        for (String raw:cache.currentIds()) {
            long id=Long.parseLong(raw);
            try {
                coordinator.locked(id,()-> {
                    if (!cache.cacheReady(id)) { cache.pause(id); return null; }
                    ShelfDecision decision=tx.execute(status -> {
                        Product product=products.selectByIdForUpdate(id); LocalDateTime now=LocalDateTime.now(clock);
                        if (product==null) throw FlashCacheService.unavailable();
                        boolean firstShelfApplied=false;
                        if (!now.isBefore(product.getFlashEndTime())) {
                            if (product.getStatus()!=ProductStatus.OFF_SHELF) setStatus(product,ProductStatus.OFF_SHELF);
                        } else if (!now.isBefore(product.getFlashStartTime()) && !cache.autoShelfDone(id)) {
                            setStatus(product,ProductStatus.ON_SHELF);
                            firstShelfApplied=true;
                        }
                        return new ShelfDecision(product,firstShelfApplied);
                    });
                    // Consume the first-shelf opportunity only when that transition committed.
                    if (decision.firstShelfApplied()) cache.markAutoShelfDone(id);
                    Product p=decision.product();
                    cache.refresh(p,shops.getRequiredShop(p.getShopId()));
                    return null;
                });
            } catch (RuntimeException e) { cache.pause(id); log.warn("Flash lifecycle paused productId={}",id); }
        }
    }
    private record ShelfDecision(Product product, boolean firstShelfApplied) {}
    private void setStatus(Product p,ProductStatus status) {
        Product update=new Product(); update.setId(p.getId()); update.setStatus(status);
        if (products.updateById(update)!=1) throw FlashCacheService.unavailable();
        p.setStatus(status);
    }
}
