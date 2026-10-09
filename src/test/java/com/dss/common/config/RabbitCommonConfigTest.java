package com.dss.common.config;

import com.dss.common.mq.DocType;
import com.dss.common.mq.SearchSyncMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RabbitMQ 公共配置的契约：消息体是 JSON，且消费者能按 __TypeId__ 反序列化回 SearchSyncMessage。
 * <p>
 * 这个用例是有来历的：把 trustedPackages 写成 "com.dss.*" 时，Spring AMQP 按"包名精确匹配"判断可信包，
 * 而 SearchSyncMessage 的包名是 com.dss.common.mq，于是发出去的消息在消费端被拒绝
 * （The class 'com.dss.common.mq.SearchSyncMessage' is not in the trusted packages）。
 * 这类问题只有在真机联调时才会暴露，所以在这里用不连中间件的方式锁住。
 */
class RabbitCommonConfigTest {

    private final MessageConverter converter = new RabbitCommonConfig().jsonMessageConverter(new ObjectMapper());

    @Test
    @DisplayName("SearchSyncMessage 能发出去也能反序列化回来（可信包配置正确）")
    void searchSyncMessageRoundTrip() {
        SearchSyncMessage payload = new SearchSyncMessage(DocType.PRODUCT, "123");

        Message message = converter.toMessage(payload, new MessageProperties());
        assertThat(new String(message.getBody())).contains("\"type\":\"PRODUCT\"").contains("\"id\":\"123\"");

        Object received = converter.fromMessage(message);
        assertThat(received).isInstanceOf(SearchSyncMessage.class);
        SearchSyncMessage back = (SearchSyncMessage) received;
        assertThat(back.getType()).isEqualTo(DocType.PRODUCT);
        assertThat(back.getId()).isEqualTo("123");
    }

    @Test
    @DisplayName("两种文档类型都能往返（SHOP / PRODUCT）")
    void bothDocTypesRoundTrip() {
        for (DocType type : DocType.values()) {
            Message message = converter.toMessage(new SearchSyncMessage(type, "9"), new MessageProperties());

            assertThat(converter.fromMessage(message)).isEqualTo(new SearchSyncMessage(type, "9"));
        }
    }

}
