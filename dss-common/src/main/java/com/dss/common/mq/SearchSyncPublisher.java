package com.dss.common.mq;

import com.dss.common.exception.NotImplementedException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 发送搜索同步事件到 dss.search.topic。放在 common 里，供 shop / product 模块调用。
 * 骨架期只有签名。
 */
@Component
@RequiredArgsConstructor
public class SearchSyncPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 店铺新增或修改后调用：发 shop.upsert，消息体 SearchSyncMessage(SHOP, shopId)。
     */
    public void publishShopChanged(Long shopId) {
        throw new NotImplementedException();
    }

    /**
     * 商品新增、修改、上下架、已售数变化后调用：发 product.upsert，消息体 SearchSyncMessage(PRODUCT, productId)。
     */
    public void publishProductChanged(Long productId) {
        throw new NotImplementedException();
    }
}
