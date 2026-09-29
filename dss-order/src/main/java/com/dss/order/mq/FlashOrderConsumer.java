package com.dss.order.mq;

import com.dss.common.constant.MqNames;
import com.dss.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 抢购落单消费者：失败会本地重试 3 次，仍失败就进 order.flash.create.dlq，由人工处理（回补 Redis 库存和已购数）。
 */
@Component
@RequiredArgsConstructor
public class FlashOrderConsumer {

    private final OrderService orderService;

    @RabbitListener(queues = MqNames.ORDER_FLASH_CREATE_QUEUE)
    public void onMessage(FlashOrderMessage message) {
        orderService.handleFlashOrderCreate(message);
    }
}
