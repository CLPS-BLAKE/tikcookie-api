# 搜索 API 与 Logstash 同步边界

已确认方案 A：实现两个公开搜索 API，保留 Logstash 为唯一索引写入方；用户澄清仅搜索不使用 RabbitMQ，通用消息队列配置和能力必须保留给其他功能。
商品搜索按既有契约过滤、排序、分页；店铺搜索匹配店名地址或分类浏览。
Java 只读 ES，保留准确 total、稳定 id 次级排序和图片 URL；错误返回 107001，超出 ES 10000 结果窗口返回 40000。
Spring 不注册搜索 MQ 拓扑/消费者，搜索发布器无操作；保留 RabbitAutoConfiguration、RabbitCommonConfig、DSS_RABBIT_* 参数和通用 JSON/投递/重试配置。
sync/rebuildAll 禁止执行，误开启动重建明确失败，不删除索引。
验收包含查询 DSL 单元测试、MockMvc 参数与响应契约、通用 MQ 可用但搜索隔离的上下文测试、显式只读真实 ES 测试和 JDK21 Maven verify。
