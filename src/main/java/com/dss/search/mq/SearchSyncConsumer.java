package com.dss.search.mq;

import com.dss.common.constant.MqNames;
import com.dss.common.mq.SearchSyncMessage;
import com.dss.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 搜索同步消费者：单消费者顺序处理（concurrency = 1）。写 ES 成功才确认；失败本地重试 3 次，
 * 仍失败就拒绝，消息进 dss.search.dlq，不无限重试（中间件配置 5.1）。
 */
@Component
@RequiredArgsConstructor
public class SearchSyncConsumer {

    private final SearchService searchService;

    @RabbitListener(queues = MqNames.SEARCH_SYNC_QUEUE, concurrency = "1")
    public void onMessage(SearchSyncMessage message) {
        searchService.sync(message);
    }
}
