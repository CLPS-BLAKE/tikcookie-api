package com.dss.flash.demo;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/** Append-only DML in the existing schema, on one Spring-managed JDBC connection. */
@Component
public class FlashDemoDataRepository {
    private static final Map<Long,String> SHOPS=Map.of(2001L,"演示·老张牛肉面馆",2002L,"演示·甜蜜蜜甜品屋",2003L,"演示·快乐星球影城");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public FlashDemoDataRepository(JdbcTemplate jdbc,PlatformTransactionManager manager) { this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); }
    private void validateManagedShops() {
        for (Map<String,Object> row:jdbc.queryForList("SELECT id,name FROM shops WHERE id IN (2001,2002,2003) FOR UPDATE")) {
            if (!SHOPS.get(((Number)row.get("id")).longValue()).equals(row.get("name")))
                throw new IllegalStateException("Reserved demo shop belongs to unrelated data; initialization refused");
        }
    }
    public void retirePrevious() {
        tx.executeWithoutResult(status -> {
            validateManagedShops();
            jdbc.update("UPDATE products SET status='OFF_SHELF',updated_at=NOW(3) WHERE type='FLASH' AND shop_id IN (2001,2002,2003)");
        });
    }
    public List<Long> append() {
        return tx.execute(status -> {
            validateManagedShops();
            Connection connection=DataSourceUtils.getConnection(jdbc.getDataSource());
            try {
                ScriptUtils.executeSqlScript(connection,new EncodedResource(new ClassPathResource("db/flash-demo-append.sql"),StandardCharsets.UTF_8));
                return List.of(jdbc.queryForObject("SELECT @demo_product_1",Long.class),
                        jdbc.queryForObject("SELECT @demo_product_2",Long.class),jdbc.queryForObject("SELECT @demo_product_3",Long.class));
            } finally { DataSourceUtils.releaseConnection(connection,jdbc.getDataSource()); }
        });
    }
}
