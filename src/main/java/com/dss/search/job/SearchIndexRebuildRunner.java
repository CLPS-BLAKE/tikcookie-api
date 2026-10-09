package com.dss.search.job;

import com.dss.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 遗留启动重建配置的保护入口。Logstash 模式不支持 Java 重建；误设 true 时明确失败，不删索引。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dss.search", name = "rebuild-on-startup", havingValue = "true")
public class SearchIndexRebuildRunner implements ApplicationRunner {

    private final SearchService searchService;

    @Override
    public void run(ApplicationArguments args) {
        searchService.rebuildAll();
    }
}
