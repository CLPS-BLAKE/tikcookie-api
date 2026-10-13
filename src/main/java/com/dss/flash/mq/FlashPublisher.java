package com.dss.flash.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Confirm/return is delivery evidence, never evidence that an order exists. No republishing. */
@Slf4j
@Component
public class FlashPublisher {
    private final RabbitTemplate rabbit;
    private final FlashMqNames names;
    public FlashPublisher(RabbitTemplate rabbit,FlashMqNames names) { this.rabbit=rabbit; this.names=names; }
    public void sendCreate(FlashOrderMessage message, Runnable onKnownRejection) {
        CorrelationData correlation=new CorrelationData(UUID.randomUUID().toString());
        correlation.getFuture().whenComplete((confirm,error)-> {
            // Nacks may be synthesized after connection loss; only a return proves non-delivery.
            if (error==null && correlation.getReturned()!=null) {
                try { onKnownRejection.run(); }
                catch (RuntimeException e) { log.warn("Unable to reconcile rejected creation: orderId={}",message.orderId()); }
            }
        });
        // Any synchronous send exception may have occurred after transmission. Caller retains association.
        rabbit.convertAndSend(names.exchange(),FlashMqConfig.CREATE_ROUTE,message,correlation);
    }
    public void sendTimeout(String orderId) {
        CorrelationData correlation=new CorrelationData(UUID.randomUUID().toString());
        correlation.getFuture().whenComplete((confirm,error)-> {
            if (error!=null || !confirm.isAck() || correlation.getReturned()!=null)
                log.warn("Payment timeout delivery uncertain; database scan remains active: orderId={}",orderId);
        });
        rabbit.convertAndSend(names.exchange(),FlashMqConfig.WAIT_ROUTE,new PaymentTimeoutMessage(orderId),correlation);
    }
}
