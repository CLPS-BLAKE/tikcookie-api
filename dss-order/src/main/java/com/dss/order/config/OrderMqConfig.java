package com.dss.order.config;

import com.dss.common.config.DssProperties;
import com.dss.common.constant.MqNames;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 订单相关的交换机、队列、绑定和死信（已实现），拓扑见 docs/中间件配置.md 6.3。
 * <p>
 * 注意：order.timeout.delay 的 TTL 在声明时定下。改了 dss.order.pay-timeout-minutes，要先在管理台删掉旧队列，
 * 否则重新声明时参数不一致，会报 PRECONDITION_FAILED。
 */
@Configuration
@RequiredArgsConstructor
public class OrderMqConfig {

    private final DssProperties properties;

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(MqNames.ORDER_EXCHANGE, true, false);
    }

    /** 抢购落单；失败进 order.flash.create.dlq。 */
    @Bean
    public Queue orderFlashCreateQueue() {
        return QueueBuilder.durable(MqNames.ORDER_FLASH_CREATE_QUEUE)
                .deadLetterExchange(MqNames.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqNames.dlq(MqNames.ORDER_FLASH_CREATE_QUEUE))
                .build();
    }

    /** 超时延迟：没有消费者，TTL 到期后死信转到 dss.order.direct / order.timeout。 */
    @Bean
    public Queue orderTimeoutDelayQueue() {
        long ttlMillis = Duration.ofMinutes(properties.getOrder().getPayTimeoutMinutes()).toMillis();
        return QueueBuilder.durable(MqNames.ORDER_TIMEOUT_DELAY_QUEUE)
                .ttl((int) ttlMillis)
                .deadLetterExchange(MqNames.ORDER_EXCHANGE)
                .deadLetterRoutingKey(MqNames.ORDER_TIMEOUT_ROUTING_KEY)
                .build();
    }

    /** 超时取消；失败进 order.timeout.dlq。 */
    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable(MqNames.ORDER_TIMEOUT_QUEUE)
                .deadLetterExchange(MqNames.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqNames.dlq(MqNames.ORDER_TIMEOUT_QUEUE))
                .build();
    }

    @Bean
    public Binding orderFlashCreateBinding() {
        return BindingBuilder.bind(orderFlashCreateQueue()).to(orderExchange()).with(MqNames.ORDER_FLASH_CREATE_ROUTING_KEY);
    }

    @Bean
    public Binding orderTimeoutDelayBinding() {
        return BindingBuilder.bind(orderTimeoutDelayQueue()).to(orderExchange()).with(MqNames.ORDER_TIMEOUT_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue()).to(orderExchange()).with(MqNames.ORDER_TIMEOUT_ROUTING_KEY);
    }

    // ---------- 死信队列（绑定在公共死信交换机 dss.dlx 上，路由键 = 队列名） ----------

    @Bean
    public Queue orderFlashCreateDlq() {
        return QueueBuilder.durable(MqNames.dlq(MqNames.ORDER_FLASH_CREATE_QUEUE)).build();
    }

    @Bean
    public Queue orderTimeoutDlq() {
        return QueueBuilder.durable(MqNames.dlq(MqNames.ORDER_TIMEOUT_QUEUE)).build();
    }

    @Bean
    public Binding orderFlashCreateDlqBinding(@Qualifier("dlxExchange") DirectExchange dlxExchange) {
        return BindingBuilder.bind(orderFlashCreateDlq()).to(dlxExchange).with(MqNames.dlq(MqNames.ORDER_FLASH_CREATE_QUEUE));
    }

    @Bean
    public Binding orderTimeoutDlqBinding(@Qualifier("dlxExchange") DirectExchange dlxExchange) {
        return BindingBuilder.bind(orderTimeoutDlq()).to(dlxExchange).with(MqNames.dlq(MqNames.ORDER_TIMEOUT_QUEUE));
    }
}
