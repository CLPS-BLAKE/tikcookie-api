package com.dss.search.mq;

import com.dss.common.constant.MqNames;
import com.dss.common.mq.SearchSyncMessage;
import com.dss.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 搜索同步消费者：失败会本地重试 3 次，仍失败就进 search.sync.dlq。
 */
@Component
@RequiredArgsConstructor
public class SearchSyncConsumer {

    private final SearchService searchService;

    @RabbitListener(queues = MqNames.SEARCH_SYNC_QUEUE)
    public void onMessage(SearchSyncMessage message) {
        searchService.sync(message);
    }
}
