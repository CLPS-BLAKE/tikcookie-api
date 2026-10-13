package com.dss.flash.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix="dss.flash",name="enabled",havingValue="true")
public class FlashMqConfig {
    public static final String EXCHANGE="dss.flash.direct";
    public static final String CREATE_ROUTE="order.create";
    public static final String CREATE_QUEUE="dss.flash.orders";
    public static final String WAIT_ROUTE="payment.wait";
    public static final String WAIT_QUEUE="dss.flash.payment.wait";
    public static final String TIMEOUT_ROUTE="payment.timeout";
    public static final String TIMEOUT_QUEUE="dss.flash.payment.timeout";
    @Bean public Declarables flashTopology(FlashMqNames names) {
        DirectExchange exchange=new DirectExchange(names.exchange(),true,false);
        Queue create=QueueBuilder.durable(names.createQueue()).build();
        Queue wait=QueueBuilder.durable(names.waitQueue()).ttl(900000).deadLetterExchange(names.exchange()).deadLetterRoutingKey(TIMEOUT_ROUTE).build();
        Queue timeout=QueueBuilder.durable(names.timeoutQueue()).build();
        return new Declarables(exchange,create,wait,timeout,
                BindingBuilder.bind(create).to(exchange).with(CREATE_ROUTE),
                BindingBuilder.bind(wait).to(exchange).with(WAIT_ROUTE),
                BindingBuilder.bind(timeout).to(exchange).with(TIMEOUT_ROUTE));
    }
    @Bean public SimpleRabbitListenerContainerFactory flashListenerFactory(ConnectionFactory connectionFactory,MessageConverter converter) {
        // Deliberately do not use Boot's configurer: no inherited retry advice/global autostart.
        SimpleRabbitListenerContainerFactory factory=new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory); factory.setMessageConverter(converter);
        factory.setConcurrentConsumers(1); factory.setMaxConcurrentConsumers(1); factory.setPrefetchCount(20);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL); factory.setDefaultRequeueRejected(false); factory.setAutoStartup(false);
        return factory;
    }
}
