package com.dss.flash.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dss.common.error.CommonErrorCode;
import com.dss.common.exception.BizException;
import com.dss.flash.model.*;
import com.dss.flash.mq.*;
import com.dss.order.mapper.OrderMapper;
import com.dss.order.model.entity.Order;
import com.dss.order.model.enums.OrderStatus;
import com.dss.order.service.OrderService;
import com.dss.order.support.OrderIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.Set;

/** DB transaction commit, reconciliation and compensation are inside the same product lock. */
@Slf4j
@Service
public class FlashOrderService {
    private static final int EXPIRY_SCAN_BATCH_SIZE=100;
    private final FlashCacheService cache;
    private final FlashRequestCoordinator coordinator;
    private final OrderMapper orders;
    private final OrderService orderService;
    private final OrderIdGenerator ids;
    private final FlashPublisher publisher;
    private final FlashRuntime runtime;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private long expiryScanOffset;

    public FlashOrderService(FlashCacheService cache,FlashRequestCoordinator coordinator,OrderMapper orders,
                             OrderService orderService,OrderIdGenerator ids,FlashPublisher publisher,FlashRuntime runtime,
                             PlatformTransactionManager tx,Clock clock) {
        this.cache=cache; this.coordinator=coordinator; this.orders=orders; this.orderService=orderService;
        this.ids=ids; this.publisher=publisher; this.runtime=runtime; this.transactions=new TransactionTemplate(tx); this.clock=clock;
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public FlashAcceptedVO submit(long user,FlashOrderDTO dto) {
        if (!runtime.isReady()) throw FlashCacheService.unavailable();
        long product;
        try { product=Long.parseLong(dto.getProductId()); } catch (RuntimeException e) { throw new BizException(CommonErrorCode.BAD_REQUEST); }
        FlashReservation r=cache.reserve(user,product,dto.getRequestKey(),String.valueOf(ids.nextId()));
        if (r.fresh()) {
            try { publisher.sendCreate(new FlashOrderMessage(r.requestId(),r.userId(),r.productId(),r.orderId()),()->terminate(r)); }
            catch (RuntimeException e) { log.warn("Creation delivery uncertain; association retained: orderId={}",r.orderId()); }
        }
        return new FlashAcceptedVO(r.orderId(),"PROCESSING",local(r.acceptedAt()),local(r.queueDeadline()));
    }
    private LocalDateTime local(long epoch) { return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch),clock.getZone()); }
    public FlashResultVO result(long user,long orderId) {
        Order o=orders.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getId,orderId).eq(Order::getUserId,user));
        return o==null?new FlashResultVO("NOT_CREATED",null,null):new FlashResultVO("CREATED",String.valueOf(o.getId()),o.getStatus());
    }
    private boolean bound(FlashReservation r,FlashOrderMessage m) {
        return r!=null && r.userId()==m.userId() && r.productId()==m.productId() && r.orderId().equals(m.orderId());
    }
    public void consume(FlashOrderMessage message) {
        if (!runtime.isReady()) throw FlashCacheService.unavailable();
        coordinator.locked(message.productId(),()-> {
            FlashReservation r=null;
            try {
                Order existing=orders.selectById(Long.valueOf(message.orderId()));
                r=cache.findRequest(message.requestId());
                if (existing!=null) {
                    if (existing.getUserId()!=message.userId() || existing.getProductId()!=message.productId() || !bound(r,message)) {
                        cache.pause(message.productId()); return null;
                    }
                    settleCommitted(existing,r); return null;
                }
                if (!bound(r,message)) { cache.pause(message.productId()); return null; }
                if (!"PENDING".equals(r.state())) return null;
                if (clock.millis()>=r.queueDeadline() || !cache.isCurrent(r.productId()) || !cache.cacheReady(r.productId())) {
                    terminate(r); return null;
                }
                FlashReservation pending=r;
                Order created=transactions.execute(tx -> {
                    Order o=orderService.createReservedFlashOrder(pending.userId(),pending.productId(),Long.parseLong(pending.orderId()),pending.queueDeadline());
                    if (clock.millis()>=pending.queueDeadline()) throw new BizException(CommonErrorCode.BAD_REQUEST,"Queue deadline elapsed");
                    return o;
                });
                // Database commit has completed here. Redis errors must NEVER enter compensation.
                try { settleCommitted(created,r); }
                catch (RuntimeException e) { cache.pause(r.productId()); log.warn("Committed order needs manual cache reconciliation: orderId={}",r.orderId()); }
            } catch (RuntimeException e) {
                // Covers definite rollback AND uncertain commit. Requery outside the old transaction/snapshot.
                try {
                    Order existing=stableLookup(message.productId(),message.orderId());
                    if (existing!=null) { if (r!=null && bound(r,message)) settleCommitted(existing,r); else cache.pause(message.productId()); }
                    else if (e instanceof BizException && r!=null && "PENDING".equals(r.state())) cache.release(r,false);
                    else cache.pause(message.productId());
                } catch (RuntimeException unavailable) {
                    cache.pause(message.productId()); log.warn("Order outcome unverifiable; product paused: orderId={}",message.orderId());
                }
            }
            return null;
        });
    }
    private void settleCommitted(Order order,FlashReservation r) {
        if (order.getUserId()!=r.userId() || order.getProductId()!=r.productId() || !String.valueOf(order.getId()).equals(r.orderId()))
            throw new IllegalStateException("Order/request association mismatch");
        if (order.getStatus()==OrderStatus.CANCELLED) cache.release(r,true);
        else {
            cache.confirm(r);
            if (order.getStatus()==OrderStatus.UNPAID) {
                try { publisher.sendTimeout(r.orderId()); }
                catch (RuntimeException e) { log.warn("Timeout delivery uncertain; scan will handle: orderId={}",r.orderId()); }
            }
        }
    }
    public void terminate(FlashReservation r) {
        coordinator.locked(r.productId(),()-> {
            try {
                Order existing=stableLookup(r.productId(),r.orderId());
                if (existing!=null) settleCommitted(existing,r);
                else {
                    FlashReservation current=cache.findRequest(r.requestId());
                    if (current!=null && "PENDING".equals(current.state())) cache.release(current,false);
                }
            } catch (RuntimeException e) { cache.pause(r.productId()); throw e; }
            return null;
        });
    }
    private Order stableLookup(long productId,String orderId) {
        return transactions.execute(tx -> orderService.lookupFlashOrder(productId,Long.valueOf(orderId)));
    }
    public synchronized void expireRequests() {
        if (!runtime.isReady()) return;
        Set<String> keys=expiryScanOffset==0?cache.expiredRequests(EXPIRY_SCAN_BATCH_SIZE)
                :cache.expiredRequests(expiryScanOffset,EXPIRY_SCAN_BATCH_SIZE);
        // Advance over retained failures too. Deletions can shift rows behind the cursor;
        // a short/empty page wraps the next round to zero so those rows are revisited.
        // A failed page read leaves the cursor untouched. Every round reads at most 100.
        expiryScanOffset=keys.size()<EXPIRY_SCAN_BATCH_SIZE?0:expiryScanOffset+keys.size();
        for (String key:keys) {
            try {
                FlashReservation r=cache.findRequest(key); if (r!=null) terminate(r);
            } catch (RuntimeException e) {
                // terminate already attempts to pause; retain the unresolved record and continue.
                log.warn("Expired request retained for manual reconciliation: requestId={}",key);
            }
        }
    }
    public void terminatePreviousRound() {
        // Startup is gated and consumers stopped, so bounded batches drain the unfinished index.
        while (true) {
            var keys=cache.pendingRequests(100); if (keys.isEmpty()) return;
            for (String key:keys) {
                FlashReservation r=cache.findRequest(key);
                if (r==null) throw new IllegalStateException("Missing previous request record");
                terminate(r);
            }
        }
    }
}
