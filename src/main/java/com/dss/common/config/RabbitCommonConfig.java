package com.dss.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 公共配置（已实现）：JSON 消息转换、confirm / return 回调。
 * 只有搜索同步用 MQ，交换机、队列和死信在 SearchMqConfig 里声明；订单不走 MQ。
 */
@Slf4j
@Configuration
public class RabbitCommonConfig {

    /** 消息体统一用 JSON，复用应用的 ObjectMapper（时间格式一致）；只信任 com.dss 包下的类。 */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper, "com.dss.*");
    }

    /** 投递失败只记日志；消息丢了由组员跑一次全量重建补回来（中间件配置 5.1）。 */
    @Bean
    public RabbitTemplateCustomizer dssRabbitTemplateCustomizer() {
        return template -> {
            template.setConfirmCallback((correlationData, ack, cause) -> {
                if (!ack) {
                    log.error("消息未到达交换机：correlation={}，cause={}", correlationData, cause);
                }
            });
            template.setReturnsCallback(returned -> log.error("消息未路由到队列：exchange={}，routingKey={}，replyText={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        };
    }
}
