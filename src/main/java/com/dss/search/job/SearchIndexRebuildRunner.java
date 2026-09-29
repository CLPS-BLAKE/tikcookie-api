package com.dss.search.job;

import com.dss.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 全量重建 ES 索引的维护入口（中间件配置 5.2）：只有 dss.search.rebuild-on-startup=true 时注册，
 * 应用启动完成后跑一次 SearchService.rebuildAll。只给部署方用，不开放接口；平时保持 false。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dss.search", name = "rebuild-on-startup", havingValue = "true")
public class SearchIndexRebuildRunner implements ApplicationRunner {

    private final SearchService searchService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始从 MySQL 全量重建 ES 索引");
        searchService.rebuildAll();
        log.info("ES 索引全量重建完成");
    }
}
