package com.dss.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 通用配置：JSON 消息转换、confirm / return 回调，供其他业务功能复用。
 * 不声明搜索拓扑、不启动搜索消费者；搜索索引同步由 Logstash 独立负责。
 */
@Slf4j
@Configuration
public class RabbitCommonConfig {

    /**
     * 消息体统一用 JSON，复用应用的 ObjectMapper（时间格式一致）；只信任 com.dss 包下的类。
     * <p>
     * 可信包必须写成"消息类所在的包名"：Spring AMQP 按包名匹配（不是通配前缀），
     * 写 "com.dss.*" 会让 com.dss.common.mq.SearchSyncMessage 被判为不可信，
     * 消费者反序列化直接失败（真机联调踩过一次，见 RabbitCommonConfigTest）。
     * 兼容已有 common.mq 消息类；新增消息类放在其他包时，需显式添加可信包，
     * 不使用通配符放宽反序列化信任范围。配置转换器不表示启用搜索 MQ 链路。
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper, "com.dss.common.mq");
    }

    /** 通用投递回调：记录未确认或未路由的消息；重试/补偿由使用 MQ 的业务自行定义。 */
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
