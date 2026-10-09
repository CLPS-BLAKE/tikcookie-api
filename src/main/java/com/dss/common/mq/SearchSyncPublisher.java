package com.dss.common.mq;

import com.dss.common.constant.MqNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * 兼容 shop/product 的变更通知调用。Spring 创建的实例无操作，索引同步交给 Logstash。
 * 显式 RabbitTemplate 构造路径仅保留为历史 MQ 辅助代码，不是当前应用的同步方案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchSyncPublisher {

    private final RabbitTemplate rabbitTemplate;

    /** Spring 明确选择此构造器，不依赖 RabbitTemplate，也不注册事务消息回调。 */
    @Autowired
    public SearchSyncPublisher() {
        this.rabbitTemplate = null;
    }

    /**
     * 店铺变更兼容通知：应用实例无操作；仅历史手工 MQ 实例投递 shop.changed。
     */
    public void publishShopChanged(Long shopId) {
        publishAfterCommit(DocType.SHOP, MqNames.SHOP_CHANGED_ROUTING_KEY, shopId);
    }

    /**
     * 商品变更兼容通知：应用实例无操作；仅历史手工 MQ 实例投递 product.changed。
     */
    public void publishProductChanged(Long productId) {
        publishAfterCommit(DocType.PRODUCT, MqNames.PRODUCT_CHANGED_ROUTING_KEY, productId);
    }

    private void publishAfterCommit(DocType type, String routingKey, Long id) {
        if (rabbitTemplate == null) {
            return;
        }
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
