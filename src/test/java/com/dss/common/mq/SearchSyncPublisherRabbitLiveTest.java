package com.dss.common.mq;

import com.dss.common.config.RabbitCommonConfig;
import com.dss.common.constant.MqNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 RabbitMQ 联调（需要 tikcookie-api/.env 的 DSS_RABBIT_*；没有 .env 或连不上时自动跳过）。
 * 用一个临时队列绑定 dss.search.direct 的 shop.changed，验证消息真的被投递、路由键正确、线上 JSON 形状正确，
 * 并验证事务提交前收不到、提交后才收到。
 */
@EnabledIfEnvironmentVariable(named = "DSS_RABBIT_LIVE_TEST", matches = "(?i)true")
class SearchSyncPublisherRabbitLiveTest {

    private static CachingConnectionFactory connectionFactory;
    private static RabbitTemplate rabbitTemplate;
    private static RabbitAdmin rabbitAdmin;
    private static Queue probeQueueDeclaration;
    private static String probeQueue;
    private static String exchange;

    @BeforeAll
    static void setUp() {
        Properties env = loadEnv();
        Assumptions.assumeTrue(env != null && !env.getProperty("DSS_RABBIT_HOST", "").isBlank(),
                "没有 tikcookie-api/.env 或未配置 RabbitMQ，跳过真实 MQ 联调");

        connectionFactory = new CachingConnectionFactory(env.getProperty("DSS_RABBIT_HOST"),
                Integer.parseInt(env.getProperty("DSS_RABBIT_PORT", "5672")));
        connectionFactory.setVirtualHost(env.getProperty("DSS_RABBIT_VHOST", "/dss"));
        connectionFactory.setUsername(env.getProperty("DSS_RABBIT_USERNAME"));
        connectionFactory.setPassword(env.getProperty("DSS_RABBIT_PASSWORD"));

        MessageConverter converter = new RabbitCommonConfig().jsonMessageConverter(new ObjectMapper());
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter);
        rabbitTemplate.setMandatory(true);

        try {
            // 真正建立一次连接，连不上就跳过，而不是让测试报一堆连接错误
            connectionFactory.createConnection().close();
            rabbitAdmin = new RabbitAdmin(connectionFactory);
            probeQueue = "dss.search.probe." + UUID.randomUUID();
            probeQueueDeclaration = new Queue(probeQueue, false, false, false);
            rabbitAdmin.declareQueue(probeQueueDeclaration);
            exchange = MqNames.SEARCH_EXCHANGE;
            DirectExchange directExchange = new DirectExchange(exchange, true, false);
            rabbitAdmin.declareExchange(directExchange);
            rabbitAdmin.declareBinding(BindingBuilder.bind(probeQueueDeclaration)
                    .to(directExchange)
                    .with(MqNames.SHOP_CHANGED_ROUTING_KEY));
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "RabbitMQ 连不上，跳过：" + e.getMessage());
        }
    }

    @AfterAll
    static void tearDown() {
        if (rabbitAdmin != null && probeQueue != null) {
            try {
                rabbitAdmin.deleteQueue(probeQueue);
            } catch (Exception ignored) {
                // 清理失败不影响结论
            }
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("无事务：消息立刻到交换机，被路由到绑定 shop.changed 的队列，线上 JSON 是 {type, id}")
    void deliversToRealBroker() {
        SearchSyncPublisher publisher = new SearchSyncPublisher(rabbitTemplate);

        publisher.publishShopChanged(987654321L);

        Message raw = rabbitTemplate.receive(probeQueue, 10_000);
        assertThat(raw).as("10 秒内应收到消息").isNotNull();
        assertThat(raw.getMessageProperties().getReceivedRoutingKey()).isEqualTo(MqNames.SHOP_CHANGED_ROUTING_KEY);

        String json = new String(raw.getBody(), StandardCharsets.UTF_8);
        assertThat(json).contains("\"type\":\"SHOP\"").contains("\"id\":\"987654321\"");

        Object converted = rabbitTemplate.getMessageConverter().fromMessage(raw);
        assertThat(converted).isInstanceOf(SearchSyncMessage.class);
        SearchSyncMessage message = (SearchSyncMessage) converted;
        assertThat(message.getType()).isEqualTo(DocType.SHOP);
        assertThat(message.getId()).isEqualTo("987654321");
    }

    @Test
    @DisplayName("商品变更走 product.changed；提交前队列里没有消息，提交后才收到")
    void productChangedWaitsForCommit() {
        // 临时再绑一个 product.changed 的探针队列，确认路由键真的不同
        String productQueue = "dss.search.probe.product." + UUID.randomUUID();
        Queue productQueueDeclaration = new Queue(productQueue, false, false, false);
        DirectExchange directExchange = new DirectExchange(exchange, true, false);
        rabbitAdmin.declareQueue(productQueueDeclaration);
        rabbitAdmin.declareBinding(BindingBuilder.bind(productQueueDeclaration)
                .to(directExchange)
                .with(MqNames.PRODUCT_CHANGED_ROUTING_KEY));
        try {
            SearchSyncPublisher publisher = new SearchSyncPublisher(rabbitTemplate);
            TransactionSynchronizationManager.initSynchronization();
            try {
                publisher.publishProductChanged(555L);

                // 事务还没提交：队列里必须什么都没有
                assertThat(rabbitTemplate.receive(productQueue, 1_500)).as("提交前不应有消息").isNull();

                // 模拟 Spring 事务提交成功
                for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCommit();
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }

            Message raw = rabbitTemplate.receive(productQueue, 10_000);
            assertThat(raw).as("提交后应收到消息").isNotNull();
            assertThat(raw.getMessageProperties().getReceivedRoutingKey()).isEqualTo(MqNames.PRODUCT_CHANGED_ROUTING_KEY);
            assertThat(new String(raw.getBody(), StandardCharsets.UTF_8)).contains("\"type\":\"PRODUCT\"");
        } finally {
            rabbitAdmin.deleteQueue(productQueue);
        }
    }

    @Test
    @DisplayName("回滚：注册了同步器但事务回滚，队列里不会有消息")
    void rollbackDeliversNothing() {
        SearchSyncPublisher publisher = new SearchSyncPublisher(rabbitTemplate);
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishShopChanged(111L);
        } finally {
            // 回滚等于不触发 afterCommit，直接清理同步器
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(rabbitTemplate.receive(probeQueue, 1_500)).as("回滚后不应有消息").isNull();
    }

    private static Properties loadEnv() {
        Path envFile = Paths.get(".env");
        if (!Files.isReadable(envFile)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(envFile)) {
            properties.load(in);
        } catch (Exception e) {
            return null;
        }
        return properties;
    }
}
