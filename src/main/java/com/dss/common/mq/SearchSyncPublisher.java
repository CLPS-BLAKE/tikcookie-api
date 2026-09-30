package com.dss.common.mq;

import com.dss.common.constant.MqNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * 发送搜索同步消息到 dss.search.direct。放在 common 里，供 shop / product 包调用。
 * 发送失败只记日志、不向调用方抛异常：此时 MySQL 已经提交，把异常抛出去会让业务方以为整个操作失败，
 * 而消息丢失由部署方跑一次全量重建（dss.search.rebuild-on-startup）补偿，不引入 outbox。
 * 消息只带类型和 ID，消费者自己查 MySQL 当前值，所以重复或乱序都不会写错。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchSyncPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 店铺新增或修改后调用：发 shop.changed，消息体 {type: SHOP, id: shopId}。
     */
    public void publishShopChanged(Long shopId) {
        publishAfterCommit(DocType.SHOP, MqNames.SHOP_CHANGED_ROUTING_KEY, shopId);
    }

    /**
     * 商品新增、修改、上下架、已售数变化后调用：发 product.changed，消息体 {type: PRODUCT, id: productId}。
     */
    public void publishProductChanged(Long productId) {
        publishAfterCommit(DocType.PRODUCT, MqNames.PRODUCT_CHANGED_ROUTING_KEY, productId);
    }

    private void publishAfterCommit(DocType type, String routingKey, Long id) {
        if (id == null) {
            log.warn("搜索同步消息未发送：type={} 的 id 为空，调用方应先保存数据再传真实主键", type);
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(type, routingKey, id);
                }
            });
            log.debug("搜索同步消息已推迟到事务提交后发送：type={}，id={}", type, id);
        } else {
            send(type, routingKey, id);
        }
    }

    /**
     * 真正投递。afterCommit 里抛出的异常会传给提交事务的调用方，所以这里必须把异常吃掉。
     */
    private void send(DocType type, String routingKey, Long id) {
        SearchSyncMessage message = new SearchSyncMessage(type, String.valueOf(id));
        // confirm 回调只拿得到 correlationData，带上类型和 ID 才能在日志里定位是哪条消息
        CorrelationData correlationData = new CorrelationData(type + ":" + id + ":" + UUID.randomUUID());
        try {
            rabbitTemplate.convertAndSend(MqNames.SEARCH_EXCHANGE, routingKey, message, correlationData);
            log.info("已发送搜索同步消息：exchange={}，routingKey={}，type={}，id={}，correlationId={}",
                    MqNames.SEARCH_EXCHANGE, routingKey, type, id, correlationData.getId());
        } catch (Exception e) {
            // 不抛给调用方：数据库已提交，业务操作本身是成功的
            log.error("搜索同步消息发送失败，业务数据已提交：exchange={}，routingKey={}，type={}，id={}，correlationId={}",
                    MqNames.SEARCH_EXCHANGE, routingKey, type, id, correlationData.getId(), e);
        }
    }
}
