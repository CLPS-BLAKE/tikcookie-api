# 搜索 API 落地与验收

日期：2026-10-08（北京时间）。范围：当前本地 feat/search-logstash-api 分支，不代表已经提交、合并或发布线上。

## 1. 原待办与当前实现

| 接口/方法 | 当前状态 | 规则 |
| --- | --- | --- |
| GET /api/v1/search/products | 已实现 | ON_SHELF；关键词 name/contentsText/shopName；分类对应 shopCategory、商品 type；综合/销量/价格升降序；分页 |
| GET /api/v1/search/shops | 已实现 | 关键词 name/address；分类；无关键词 createdAt 倒序、有关键词相关度排序；分页 |
| sync(message) | 当前方案禁止执行 | 搜索不使用 RabbitMQ；由 Logstash 唯一写入索引 |
| rebuildAll() | 当前方案禁止执行 | 不删除索引；维护按 Logstash 流程，不新增维护 API |

两接口公开，返回 Result 包裹的 PageResult（list/total/page/size），ID 为字符串、金额为整数分、枚举不变。图片 ObjectKey 通过 FileUrlResolver 转换。空白关键词按无关键词；无命中时 list=[]、total=0。

default 有关键词时 _score 倒序再 soldCount 倒序，无关键词仅销量倒序；sales 按销量，price_asc/price_desc 按价格。店铺无关键词按 createdAt 倒序。同值追加 id keyword 升序，不使用 ES _id 排序。精确统计 total。page 默认 1、size 默认 10/最大 50；page × size 不得超过 10000，超过返回 HTTP 400 / 40000；该窗口不意味着 total 最多 10000。

ES 查询/映射失败返回 HTTP 200、code=107001、msg=搜索服务暂不可用；原始异常只进服务端日志，不作为空结果或返回客户端。商品详情及库存继续从 MySQL 读取。

## 2. 仅搜索不使用 RabbitMQ，保留通用 MQ 能力

- 启动类保留 RabbitAutoConfiguration；通用 ConnectionFactory、RabbitTemplate、RabbitAdmin 仍可供其他业务使用。
- RabbitCommonConfig 注册为公共配置，保留 JSON 转换器和 confirm/return 回调，不依赖搜索索引重建作补偿。
- SearchMqConfig、SearchSyncConsumer 不注册到 Spring，不声明搜索拓扑、不启动搜索消费者；其他业务可以独立定义自己的队列和消费者。
- Spring 创建的 SearchSyncPublisher 是无操作实例，兼容 shop/product 的既有调用；不发消息、不注册事务回调。手工 RabbitTemplate 构造路径仅供历史测试，不是应用行为。
- application.yml 和 .env.example 保留 DSS_RABBIT_* 连接配置及通用投递/重试设置；本次不修改真实 .env、不关闭服务器 MQ，也不删除旧消息。
- DSS_SEARCH_REBUILD_ON_STARTUP 必须 false；误设 true 会明确启动失败，提示 Logstash 维护，不执行任何 ES 写入/删除。

## 3. 请求示例

```http
GET /api/v1/search/products?keyword=牛肉面&category=FOOD&type=NORMAL&sort=price_asc&page=1&size=10
GET /api/v1/search/products?sort=sales&page=2&size=10
GET /api/v1/search/shops?category=FOOD&page=1&size=10
GET /api/v1/search/shops?keyword=人民路&page=1&size=10
```

## 4. 可重复验证

工作目录 tikcookie-api，JAVA_HOME 指向 JDK 21。默认构建只执行离线测试，真实测试均显式开启。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -B --no-transfer-progress verify

# 只读 ES + 实际服务/Controller，不启动数据库、Redis、MQ 或 OSS 客户端。
$env:DSS_ES_LIVE_TEST = 'true'
mvn -B --no-transfer-progress '-Dtest=SearchApiEsLiveTest' test
Remove-Item Env:DSS_ES_LIVE_TEST
```

测试仅读取 .env 的 ES 凭据和图片前缀（进程环境优先），不打印凭据。真实联调要求既有上架商品和店铺；测试读取不超过 1000 个文档作快照，不支持拿大规模生产数据执行该教学验收。使用现有数据验证所有排序、关键词、分类、类型、分页、总数、图片和 HTTP 输出。索引若在测试过程中变化，可导致快照断言失败；需在稳定验收窗口复验，不能把失败静默跳过。

LogstashApplicationStartupTest 使用完整 Spring Boot 配置但所有地址为本地不可用端口、不导入 .env，验证通用 MQ bean 存在、没有搜索队列或消费者、搜索查询/变更通知不调用 RabbitTemplate；ES 不可用时两个公开接口返回 107001 而不是 401/501。日期测试使用实际 Spring Data Elasticsearch 映射器和应用 JacksonConfig，不修改线上数据。

## 5. 证据与边界

已执行搜索测试的失败基线，再实现查询；39 项最初搜索测试通过。搜索 MQ 隔离的 5 项配置测试也经历失败到通过；后续根据用户澄清，改为验证保留通用 MQ 而不是全局关闭。此前只读 ES 联调快照为 30 个上架 NORMAL 商品、5 个店铺；NORMAL/FLASH 类型筛选包含空类型结果验证。日期映射、FLASH VO 及 HTTP 时间格式由离线测试验证；当前索引没有 FLASH 商品，不能声称真实 FLASH 导入已验收。

真实 ES 与定向回归已执行 72 项，0 失败/错误/跳过，其中 SearchApiEsLiveTest 展开为 11 项真实只读测试。完整应用离线启动另有 2 项通过。

此前 JDK 21 clean verify 以及通用 MQ 范围修正后的 mvn -B --no-transfer-progress verify 均通过：189 项计入报告，172 项执行通过、17 项按显式开关跳过，0 失败、0 错误，BUILD SUCCESS；生成 target/dss-0.0.1-SNAPSHOT.jar。跳过分别为 ES 的 7 个测试方法（开启后参数化展开 11 项）、历史 MQ 3 项、真实 OSS 7 项；不把跳过记为已验收。新增范围回归断言通用 ConnectionFactory/RabbitTemplate/RabbitAdmin/监听器基础设施存在、公共 JSON 转换器可处理非搜索消息；仅禁止搜索拓扑/消费者/发布，不禁止未来其他业务的队列/监听器。本次修正未重新连接真实 ES 或 MQ，也不修改 .env。构建仍有原 Maven 全局 settings.xml 的 profile 标签警告及已有依赖/Mockito 提示，本次未修改全局工具配置。

未验收或未执行：线上应用发布、前端页面接入、MySQL 更新时间到 ES 的增量/上下架变更、关联更新、Logstash 重启恢复、故障重放及容量；不为此创建或修改线上业务数据。更新/恢复注意事项见 LOGSTASH_SYNC.md。
