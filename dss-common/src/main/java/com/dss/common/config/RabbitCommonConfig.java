package com.dss.common.config;

import com.dss.common.constant.MqNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 公共配置（已实现）：JSON 消息转换、confirm / return 回调、公共死信交换机 dss.dlx。
 * 各模块的交换机和队列在各自的 config 里声明（OrderMqConfig、SearchMqConfig）。
 */
@Slf4j
@Configuration
public class RabbitCommonConfig {

    /** 消息体统一用 JSON，复用应用的 ObjectMapper（时间格式一致）；只信任 com.dss 包下的类。 */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper, "com.dss.*");
    }

    /** 公共死信交换机：业务队列重试 3 次仍失败的消息进这里，再路由到各自的 .dlq 队列。 */
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MqNames.DLX_EXCHANGE, true, false);
    }

    /** 投递失败只记日志；需要补偿的，由业务自己处理。 */
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
