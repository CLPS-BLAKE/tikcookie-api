package com.dss.order.mq;

import com.dss.common.exception.NotImplementedException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单消息生产者（骨架期只有签名）。交换机和路由键用 MqNames 常量。
 */
@Component
@RequiredArgsConstructor
public class OrderMessageSender {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 投递抢购落单消息：dss.order.direct / order.flash.create。
     */
    public void sendFlashOrderCreate(FlashOrderMessage message) {
        throw new NotImplementedException();
    }

    /**
     * 投递超时延迟消息：dss.order.direct / order.timeout.delay。
     * 队列 TTL（dss.order.pay-timeout-minutes）到期后，消息经死信转到 order.timeout。
     */
    public void sendPayTimeoutDelay(OrderTimeoutMessage message) {
        throw new NotImplementedException();
    }
}
