# Logstash 搜索索引同步部署记录

更新日期：2026-10-08（北京时间）。依据部署负责人提供的服务器输出及“已成功部署 Logstash 并同步”的确认记录，不是本地重新执行云端验收的结果。

## 1. 已确认状态与证据

服务器部署目录为 `/opt/tikcookie-data`，通过 `docker compose -f compose.yml` 管理 MySQL、ES、Logstash；不据此推断应用或其他中间件的运行方式。当前同步方案为 **MySQL → Logstash JDBC 轮询 → Elasticsearch**，不是 RabbitMQ 消费者写索引。

| 核对项 | MySQL | Elasticsearch |
| --- | --- | --- |
| 店铺 | `shops`：5 条 | `dss_shop/_count`：5，失败分片 0 |
| 商品 | `products INNER JOIN shops`：30 条 | `dss_product/_count`：30，失败分片 0 |
| 店铺 mapping | — | ID/分类 keyword，名称/地址 text，创建时间 date |
| 商品 mapping | — | 金额 long、销量 integer，ID/分类/类型/状态 keyword，搜索字段 text |
| 时间格式 | — | `createdAt`、`flashStartTime`、`flashEndTime` 为 `yyyy-MM-dd HH:mm:ss` |

两个索引已创建，负责人确认部署及同步成功。数量/mapping 有命令输出；未逐项提供的字段抽样、增量变更、重启恢复、故障重放与容量测试不登记为全部通过。5/30 是此次快照，不是固定数据量。确切启动时间、实机镜像摘要/版本与环境配置版本尚未登记。

## 2. 配置与同步规则

交付配置为 Logstash 8.19.22、Connector/J 8.4.0；实机版本以部署登记为准。配置源文件目前在本地 `D:\desktop\fsdownload`（`compose.yml`、`logstash/`），服务器在上述目录运行，尚未归档至仓库 `deploy/`。真实 `.env` 不进入仓库。

- 每分钟轮询，无游标时首次全量读取；以字符串主键作为 ES `_id` 覆盖写入，字段与 Java `ShopDoc`、`ProductDoc` 对齐。
- 店铺依据 `shops.updated_at`；商品依据 `GREATEST(products.updated_at, shops.updated_at)`，使店铺改名/分类触发商品冗余字段刷新。
- `contentsText` 提取 `contents[].items[].name`；图片为 OSS ObjectKey，时间为上海时区本地字符串。
- 两个独立游标及持久队列在 `logstash_data`。有 5 分钟回读窗口、10 秒筛选延迟，允许分钟级延迟，不是 CDC 或跨系统强一致。
- 新增、修改、销量、上下架必须维护 `updated_at`。物理 DELETE 不自动删除 ES 文档；超出回读窗口才提交的事务、回拨时间或不维护更新时间的手工 SQL 可能漏读。
- 交付限额为 768 MiB、JVM 堆 256 MiB，新增进程须纳入 node1 的 2C4G 整体预算；部署成功不代表容量验收通过。

## 3. 应用与 RabbitMQ 边界

2026-10-08 本地功能分支已实现 Java searchProducts/searchShops，两公开接口只读 ES；只读真实 ES 联调和搜索 MQ 隔离验证见 [SEARCH_API.md](SEARCH_API.md)。本地验证不是线上应用已更新的证明，也不代表页面搜索已接入。

用户澄清仅搜索不使用 RabbitMQ，其他业务仍需通用消息队列：保留 DSS_RABBIT_* 连接配置、RabbitAutoConfiguration 和 RabbitCommonConfig 的 JSON/confirm/return 能力。仅 SearchMqConfig/SearchSyncConsumer 不注册；Spring 创建的 SearchSyncPublisher 无操作，不投递搜索消息、不注册搜索事务回调。保留的手工搜索 MQ 辅助代码和历史测试不代表启用搜索链路。旧搜索队列/消息不会被本应用消费或删除，需部署方单独评估处置；不关闭服务器 RabbitMQ 或阻碍其他业务的队列/消费者。

Java sync/rebuildAll 显式拒绝执行，不写入/删除索引。DSS_SEARCH_REBUILD_ON_STARTUP=false 必须保持关闭，误设 true 会明确启动失败。故障补偿需先修复原因，再停机维护重置 JDBC 游标或临时 clean_run => true 从 MySQL 全量覆盖，完成后恢复增量配置；这不是删除并重建索引。物理删除残留、错误 mapping 须备份并另行维护。禁止 down -v 清空业务卷。

## 4. 维护与后续证据

在服务器部署目录执行；若原部署使用 `-p`，继续保留原项目名。口令交互输入，不保存到文档。

```sh
docker compose -f compose.yml ps logstash
docker compose -f compose.yml logs --tail=100 logstash
docker compose -f compose.yml exec mysql mysql -uroot -p -D dss -e 'SELECT COUNT(*) AS shops FROM shops; SELECT COUNT(*) AS products FROM products p INNER JOIN shops s ON s.id=p.shop_id;'
curl -u elastic 'http://127.0.0.1:9200/dss_shop/_count?pretty'
curl -u elastic 'http://127.0.0.1:9200/dss_product/_count?pretty'
curl -u elastic 'http://127.0.0.1:9200/dss_shop/_mapping?pretty'
curl -u elastic 'http://127.0.0.1:9200/dss_product/_mapping?pretty'
```

后续补录字段抽样、商品更新/上下架、店铺改名/分类关联更新、游标重启恢复、异常重放和容量证据。完整部署/维护步骤见部署目录 `logstash/README.md`。
