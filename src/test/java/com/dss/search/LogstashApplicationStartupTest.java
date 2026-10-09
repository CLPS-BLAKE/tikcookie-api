package com.dss.search;

import com.dss.DssApplication;
import com.dss.common.mq.SearchSyncPublisher;
import com.dss.common.config.RabbitCommonConfig;
import com.dss.search.config.SearchMqConfig;
import com.dss.search.mq.SearchSyncConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 完整应用配置加载，但所有地址均为本地不可用端口且不导入 .env，不触碰真实中间件。 */
@SpringBootTest(classes = DssApplication.class, properties = {
        "spring.config.import=", "spring.profiles.active=test", "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/dss", "spring.datasource.username=test", "spring.datasource.password=test",
        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1", "spring.data.redis.password=test",
        "spring.rabbitmq.host=127.0.0.1", "spring.rabbitmq.port=1", "spring.rabbitmq.virtual-host=/dss",
        "spring.rabbitmq.username=test", "spring.rabbitmq.password=test",
        "spring.elasticsearch.uris=http://127.0.0.1:1", "spring.elasticsearch.username=", "spring.elasticsearch.password=",
        "spring.elasticsearch.connection-timeout=100ms", "spring.elasticsearch.socket-timeout=100ms",
        "dss.file.oss.endpoint=http://127.0.0.1:1", "dss.file.oss.bucket=test-bucket", "dss.file.oss.region=cn-guangzhou",
        "dss.file.oss.access-key-id=test", "dss.file.oss.access-key-secret=test",
        "dss.job.enabled=false", "dss.search.rebuild-on-startup=false", "springdoc.api-docs.enabled=false"
})
@AutoConfigureMockMvc
class LogstashApplicationStartupTest {
    @Autowired private ApplicationContext context;
    @Autowired private MockMvc mvc;
    @MockitoSpyBean private RabbitTemplate rabbitTemplate;

    @Test
    void fullApplicationKeepsRabbitInfrastructureButNoSearchTopologyOrMessages() {
        assertThat(context.getBeansOfType(ConnectionFactory.class)).hasSize(1);
        assertThat(context.getBeansOfType(RabbitTemplate.class)).hasSize(1);
        assertThat(context.getBeansOfType(RabbitAdmin.class)).hasSize(1);
        assertThat(context.getBeansOfType(RabbitCommonConfig.class)).hasSize(1);
        assertThat(context.getBeansOfType(SearchMqConfig.class)).isEmpty();
        // 仅禁止搜索队列，不限制今后其他业务声明自己的队列或监听器。
        assertThat(context.getBeansOfType(Queue.class).values())
                .noneMatch(queue -> queue.getName().startsWith("dss.search."));
        assertThat(context.getBeansOfType(SearchSyncConsumer.class)).isEmpty();
        assertThat(context.getBeansOfType(RabbitListenerEndpointRegistry.class)).hasSize(1);
        MessageConverter converter = context.getBean(MessageConverter.class);
        assertThat(rabbitTemplate.getMessageConverter()).isSameAs(converter);
        // 非搜索业务可以复用公共 JSON 转换能力，不需要启用搜索链路。
        Map<String, String> payload = Map.of("event", "future.feature");
        assertThat(converter.fromMessage(converter.toMessage(payload, new MessageProperties()))).isEqualTo(payload);
        TransactionSynchronizationManager.initSynchronization();
        try {
            clearInvocations(rabbitTemplate);
            var publisher = context.getBean(SearchSyncPublisher.class);
            publisher.publishShopChanged(1L);
            publisher.publishProductChanged(2L);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
            verifyNoInteractions(rabbitTemplate);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void bothPublicEndpointsReturn107001InsteadOf401Or501WhenEsIsDown() throws Exception {
        clearInvocations(rabbitTemplate);
        for (String endpoint : List.of("products", "shops")) {
            mvc.perform(get("/api/v1/search/" + endpoint)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(107001))
                    .andExpect(jsonPath("$.msg").value("搜索服务暂不可用"));
        }
        verifyNoInteractions(rabbitTemplate);
    }
}
