package com.dss.common.mq;

import com.dss.common.constant.MqNames;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 搜索同步消息发布器：
 * 事务里调用要等提交后才发、回滚不发、无事务直接发；消息体 {type, id 字符串}；
 * 发送失败只记日志，不把异常抛给已经提交成功的调用方。
 */
class SearchSyncPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RabbitTemplate rabbitTemplate;
    private SearchSyncPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new SearchSyncPublisher(rabbitTemplate);
        clearTransactionState();
    }

    @AfterEach
    void tearDown() {
        clearTransactionState();
    }

    @Test
    @DisplayName("无事务时直接发送：交换机 dss.search.direct + 路由键 shop.changed")
    void shopChangedWithoutTransaction() {
        publisher.publishShopChanged(42L);

        SearchSyncMessage sent = captureMessage(MqNames.SHOP_CHANGED_ROUTING_KEY);
        assertThat(sent.getType()).isEqualTo(DocType.SHOP);
        assertThat(sent.getId()).isEqualTo("42");
    }

    @Test
    @DisplayName("无事务时直接发送：路由键 product.changed，ID 是字符串")
    void productChangedWithoutTransaction() {
        publisher.publishProductChanged(123L);

        SearchSyncMessage sent = captureMessage(MqNames.PRODUCT_CHANGED_ROUTING_KEY);
        assertThat(sent.getType()).isEqualTo(DocType.PRODUCT);
        assertThat(sent.getId()).isEqualTo("123");
    }

    @Test
    @DisplayName("消息体 JSON 是 {type, id}，ID 用字符串（中间件配置 5.1 的示例格式）")
    void messageJsonShape() throws Exception {
        publisher.publishProductChanged(123L);

        SearchSyncMessage sent = captureMessage(MqNames.PRODUCT_CHANGED_ROUTING_KEY);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(sent));

        assertThat(fieldNames(json)).containsExactlyInAnyOrder("type", "id");
        assertThat(json.get("type").asText()).isEqualTo("PRODUCT");
        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("id").asText()).isEqualTo("123");
    }

    @Test
    @DisplayName("事务内调用：提交前不发消息")
    void defersUntilCommit() {
        beginTransaction();

        publisher.publishProductChanged(123L);

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("事务内调用：提交后才发消息")
    void sendsAfterCommit() {
        beginTransaction();

        publisher.publishProductChanged(123L);
        verifyNoInteractions(rabbitTemplate);

        commit();

        SearchSyncMessage sent = captureMessage(MqNames.PRODUCT_CHANGED_ROUTING_KEY);
        assertThat(sent.getId()).isEqualTo("123");
    }

    @Test
    @DisplayName("事务回滚：一条消息都不发")
    void sendsNothingOnRollback() {
        beginTransaction();

        publisher.publishShopChanged(42L);
        publisher.publishProductChanged(123L);
        rollback();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("同一个事务里的多条变更按注册顺序在提交后发出")
    void sendsAllMessagesAfterCommit() {
        beginTransaction();

        publisher.publishShopChanged(1L);
        publisher.publishProductChanged(2L);
        publisher.publishProductChanged(3L);
        commit();

        verify(rabbitTemplate).convertAndSend(eq(MqNames.SEARCH_EXCHANGE), eq(MqNames.SHOP_CHANGED_ROUTING_KEY),
                eq(new SearchSyncMessage(DocType.SHOP, "1")), any(CorrelationData.class));
        verify(rabbitTemplate).convertAndSend(eq(MqNames.SEARCH_EXCHANGE), eq(MqNames.PRODUCT_CHANGED_ROUTING_KEY),
                eq(new SearchSyncMessage(DocType.PRODUCT, "2")), any(CorrelationData.class));
        verify(rabbitTemplate).convertAndSend(eq(MqNames.SEARCH_EXCHANGE), eq(MqNames.PRODUCT_CHANGED_ROUTING_KEY),
                eq(new SearchSyncMessage(DocType.PRODUCT, "3")), any(CorrelationData.class));
    }

    @Test
    @DisplayName("ID 为空：不发送，也不抛异常")
    void ignoresNullId() {
        publisher.publishShopChanged(null);
        publisher.publishProductChanged(null);

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("无事务且发送失败：异常不外抛（数据库这时可能已经改好了）")
    void sendFailureIsSwallowedWithoutTransaction() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(SearchSyncMessage.class),
                        any(CorrelationData.class));

        assertThatCode(() -> publisher.publishProductChanged(123L)).doesNotThrowAnyException();

        verify(rabbitTemplate).convertAndSend(eq(MqNames.SEARCH_EXCHANGE), eq(MqNames.PRODUCT_CHANGED_ROUTING_KEY),
                any(SearchSyncMessage.class), any(CorrelationData.class));
    }

    @Test
    @DisplayName("提交后发送失败：afterCommit 不抛异常，不会把已经提交的事务回报成失败")
    void sendFailureIsSwallowedAfterCommit() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(SearchSyncMessage.class),
                        any(CorrelationData.class));
        beginTransaction();
        publisher.publishProductChanged(123L);

        assertThatCode(this::commit).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("correlationId 里带类型和 ID，confirm 失败时日志能定位到是哪条消息")
    void correlationDataIdentifiesMessage() {
        publisher.publishProductChanged(123L);

        ArgumentCaptor<CorrelationData> captor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(eq(MqNames.SEARCH_EXCHANGE), eq(MqNames.PRODUCT_CHANGED_ROUTING_KEY),
                any(SearchSyncMessage.class), captor.capture());

        assertThat(captor.getValue().getId()).startsWith("PRODUCT:123:");
    }

    @Test
    @DisplayName("交换机名和路由键和拓扑一致（只有一个搜索同步交换机）")
    void namesMatchTopology() {
        assertThat(MqNames.SEARCH_EXCHANGE).isEqualTo("dss.search.direct");
        assertThat(MqNames.SHOP_CHANGED_ROUTING_KEY).isEqualTo("shop.changed");
        assertThat(MqNames.PRODUCT_CHANGED_ROUTING_KEY).isEqualTo("product.changed");
        assertThat(MqNames.SEARCH_SYNC_QUEUE).isEqualTo("dss.search.sync");
    }

    @Test
    @DisplayName("RabbitTemplate 第 3 个参数是消息对象本身（JSON 由 Jackson2JsonMessageConverter 转换）")
    void sendsMessageObjectNotPreSerializedJson() {
        publisher.publishShopChanged(42L);

        SearchSyncMessage sent = captureMessage(MqNames.SHOP_CHANGED_ROUTING_KEY);
        assertThat(sent).isEqualTo(new SearchSyncMessage(DocType.SHOP, "42"));
    }

    private SearchSyncMessage captureMessage(String routingKey) {
        ArgumentCaptor<SearchSyncMessage> captor = ArgumentCaptor.forClass(SearchSyncMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(MqNames.SEARCH_EXCHANGE), eq(routingKey), captor.capture(),
                any(CorrelationData.class));
        return captor.getValue();
    }

    private List<String> fieldNames(JsonNode json) {
        List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    /** 模拟 Spring 在事务提交成功后触发的 afterCommit。 */
    private void commit() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        clearTransactionState();
    }

    /** 模拟回滚：不触发 afterCommit，直接清理同步器。 */
    private void rollback() {
        clearTransactionState();
    }

    private void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
