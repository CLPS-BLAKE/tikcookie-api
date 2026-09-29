package com.dss.search.config;

import com.dss.common.constant.MqNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 搜索同步的交换机、队列、绑定和死信（已实现），拓扑见 docs/中间件配置.md 6.3。
 */
@Configuration
public class SearchMqConfig {

    @Bean
    public TopicExchange searchExchange() {
        return new TopicExchange(MqNames.SEARCH_EXCHANGE, true, false);
    }

    /** 搜索同步；失败进 search.sync.dlq。 */
    @Bean
    public Queue searchSyncQueue() {
        return QueueBuilder.durable(MqNames.SEARCH_SYNC_QUEUE)
                .deadLetterExchange(MqNames.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqNames.dlq(MqNames.SEARCH_SYNC_QUEUE))
                .build();
    }

    @Bean
    public Binding searchShopBinding() {
        return BindingBuilder.bind(searchSyncQueue()).to(searchExchange()).with(MqNames.SEARCH_SHOP_BINDING);
    }

    @Bean
    public Binding searchProductBinding() {
        return BindingBuilder.bind(searchSyncQueue()).to(searchExchange()).with(MqNames.SEARCH_PRODUCT_BINDING);
    }

    @Bean
    public Queue searchSyncDlq() {
        return QueueBuilder.durable(MqNames.dlq(MqNames.SEARCH_SYNC_QUEUE)).build();
    }

    @Bean
    public Binding searchSyncDlqBinding(@Qualifier("dlxExchange") DirectExchange dlxExchange) {
        return BindingBuilder.bind(searchSyncDlq()).to(dlxExchange).with(MqNames.dlq(MqNames.SEARCH_SYNC_QUEUE));
    }
}
