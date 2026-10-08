package com.dss.search.config;

import com.dss.common.constant.MqNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;

/**
 * 历史 MQ 拓扑辅助代码，不注册为 Spring 配置；Logstash 模式不声明交换机或队列。
 */
public class SearchMqConfig {

    @Bean
    public DirectExchange searchExchange() {
        return new DirectExchange(MqNames.SEARCH_EXCHANGE, true, false);
    }

    /** 搜索同步队列；重试用完仍失败的消息转到 dss.search.dlx，进 dss.search.dlq。 */
    @Bean
    public Queue searchSyncQueue() {
        return QueueBuilder.durable(MqNames.SEARCH_SYNC_QUEUE)
                .deadLetterExchange(MqNames.SEARCH_DLX)
                .deadLetterRoutingKey(MqNames.SEARCH_DLQ)
                .build();
    }

    @Bean
    public Binding searchShopChangedBinding() {
        return BindingBuilder.bind(searchSyncQueue()).to(searchExchange()).with(MqNames.SHOP_CHANGED_ROUTING_KEY);
    }

    @Bean
    public Binding searchProductChangedBinding() {
        return BindingBuilder.bind(searchSyncQueue()).to(searchExchange()).with(MqNames.PRODUCT_CHANGED_ROUTING_KEY);
    }

    @Bean
    public DirectExchange searchDlx() {
        return new DirectExchange(MqNames.SEARCH_DLX, true, false);
    }

    /** 死信队列：没有消费者，供组员在管理台检查；问题处理完后跑一次全量重建。 */
    @Bean
    public Queue searchDlq() {
        return QueueBuilder.durable(MqNames.SEARCH_DLQ).build();
    }

    @Bean
    public Binding searchDlqBinding() {
        return BindingBuilder.bind(searchDlq()).to(searchDlx()).with(MqNames.SEARCH_DLQ);
    }
}
