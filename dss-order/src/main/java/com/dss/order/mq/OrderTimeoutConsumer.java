package com.dss.order.mq;

import com.dss.common.constant.MqNames;
import com.dss.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 超时取消消费者：消费 order.timeout（来自 order.timeout.delay 的死信）。
 */
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final OrderService orderService;

    @RabbitListener(queues = MqNames.ORDER_TIMEOUT_QUEUE)
    public void onMessage(OrderTimeoutMessage message) {
        orderService.handlePayTimeout(message.getOrderId());
    }
}
