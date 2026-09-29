package com.dss.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 抢购落单消息（路由键 order.flash.create）。orderId 在 Lua 预扣成功后生成，消费者用它作 _id，保证幂等。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlashOrderMessage {

    private Long orderId;

    private Long userId;

    private Long productId;

    private LocalDateTime createTime;
}
