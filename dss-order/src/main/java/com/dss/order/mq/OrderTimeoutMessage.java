package com.dss.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 超时延迟消息：投到 order.timeout.delay，队列 TTL 到期后经死信转到 order.timeout。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage {

    private Long orderId;
}
