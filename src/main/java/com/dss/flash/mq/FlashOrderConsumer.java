package com.dss.flash.mq;

import com.dss.flash.service.FlashOrderService;
import com.dss.order.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="dss.flash",name="enabled",havingValue="true")
public class FlashOrderConsumer {
    private final FlashOrderService flashOrders;
    private final OrderService orders;
    @RabbitListener(id="flashOrders",queues="#{@flashMqNames.createQueue()}",containerFactory="flashListenerFactory",autoStartup="false")
    public void create(FlashOrderMessage message,Channel channel,@Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try { flashOrders.consume(message); }
        catch (RuntimeException e) { log.warn("Creation message rejected without automatic retry: orderId={}",message.orderId()); channel.basicReject(tag,false); return; }
        // Service returns after DB commit/reconciliation. Channel failure may legitimately cause redelivery.
        channel.basicAck(tag,false);
    }
    @RabbitListener(id="flashPaymentTimeouts",queues="#{@flashMqNames.timeoutQueue()}",containerFactory="flashListenerFactory",autoStartup="false")
    public void timeout(PaymentTimeoutMessage message,Channel channel,@Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try { orders.cancelOverdueOrder(Long.valueOf(message.orderId())); }
        catch (RuntimeException e) { log.warn("Timeout message rejected; database scan retained: orderId={}",message.orderId()); channel.basicReject(tag,false); return; }
        channel.basicAck(tag,false);
    }
}
