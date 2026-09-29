package com.dss.common.mq;

import com.dss.common.exception.NotImplementedException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 发送搜索同步消息到 dss.search.direct。放在 common 里，供 shop / product 包调用。
 * 必须等 MySQL 事务提交之后再发（在事务里调用时用 TransactionSynchronization.afterCommit 推迟），
 * 否则消费者可能读到提交前的旧数据（需求文档 4.3）。骨架期只有签名。
 */
@Component
@RequiredArgsConstructor
public class SearchSyncPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 店铺新增或修改后调用：发 shop.changed，消息体 {type: SHOP, id: shopId}。
     */
    public void publishShopChanged(Long shopId) {
        throw new NotImplementedException();
    }

    /**
     * 商品新增、修改、上下架、已售数变化后调用：发 product.changed，消息体 {type: PRODUCT, id: productId}。
     */
    public void publishProductChanged(Long productId) {
        throw new NotImplementedException();
    }
}
