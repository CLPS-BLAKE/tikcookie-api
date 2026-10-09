package com.dss.search.mq;

import com.dss.common.constant.MqNames;
import com.dss.common.mq.SearchSyncMessage;
import com.dss.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * 历史 MQ 消费入口，不注册为 Spring 组件。当前索引写入仅由 Logstash 执行。
 */
@RequiredArgsConstructor
public class SearchSyncConsumer {

    private final SearchService searchService;

    @RabbitListener(queues = MqNames.SEARCH_SYNC_QUEUE, concurrency = "1")
    public void onMessage(SearchSyncMessage message) {
        searchService.sync(message);
    }
}
