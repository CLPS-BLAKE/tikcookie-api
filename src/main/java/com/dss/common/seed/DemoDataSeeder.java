package com.dss.common.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 抢购演示数据播种器：dss.demo.seed-enabled=true 时生效（默认关闭）。
 * 应用启动完成后，以 products.id=3001 作为“是否已播种”的标记——库里还没有演示数据时才执行
 * classpath 下的 db/dss-demo-data.sql 灌一遍；已存在则跳过，不覆盖演示过程中的改动。
 * 只灌数据、不建表（建表见仓库根目录 dss-init.sql，由管理账号手动执行），表不存在时打日志跳过。
 * 只写 MySQL 业务数据，不参与搜索/ES——索引由部署侧 Logstash 从 MySQL 同步，本类不触发任何索引写入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dss.demo", name = "seed-enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    /** 标记商品：抢购演示数据里固定存在的商品 ID（见 db/dss-demo-data.sql 的 3001）。 */
    private static final long DEMO_MARKER_PRODUCT_ID = 3001L;

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer markerCount;
        try {
            markerCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM products WHERE id = ?", Integer.class, DEMO_MARKER_PRODUCT_ID);
        } catch (DataAccessException e) {
            log.warn("演示数据播种跳过：products 表不可用（请先执行 dss-init.sql 建表）。原因：{}", e.getMessage());
            return;
        }
        if (markerCount != null && markerCount > 0) {
            log.info("抢购演示数据已存在（products.id=3001），跳过播种；如需重置可删除演示数据后重启，或手动执行 db/dss-demo-data.sql");
            return;
        }
        log.info("开始播种抢购演示数据：classpath:db/dss-demo-data.sql");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/dss-demo-data.sql"));
        populator.setContinueOnError(false);
        DatabasePopulatorUtils.execute(populator, dataSource);
        log.info("抢购演示数据播种完成");
    }
}
