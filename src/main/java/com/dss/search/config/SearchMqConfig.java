package com.dss.search.config;

import com.dss.common.constant.MqNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 搜索同步的交换机、队列、绑定和死信（已实现），拓扑见 docs/中间件配置.md 5.1。
 * 应用连上 RabbitMQ 时由 RabbitAdmin 自动声明，全部持久化；项目里只有这一条 MQ 链路。
 */
@Configuration
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
