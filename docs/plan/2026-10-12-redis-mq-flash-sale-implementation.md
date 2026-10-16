# Redis + RabbitMQ 秒杀落地记录（历史计划）

> 本文保留当时的实施和测试记录，不证明本次交付运行结果。历史测试文件不一定在当前仓库中，当前验证方法见[测试说明](../TESTING.md)。

> 原规划：`D:/codespace/TikCookie/docs/plan/2026-10-12-redis-mq-flash-sale.md`。
> 工作分支：两个独立仓库均为 `feat/redis-mq-flash-sale`。原有图片、OSS 测试、部署脚本等未提交工作保留；本次不自动提交/推送，不执行服务器发布。
> 实施跨 2026-10-12 / 2026-10-13（Asia/Shanghai），状态：八阶段代码与本地验收完成；独立规范/质量审查发现的问题已逐项RED→GREEN修复并复审通过。Git提交、合并及服务器发布未执行。

## 参数

三个新演示商品每轮追加；价格 100/1680/1990 分，库存 10/20/30，限购 1/2/2，开抢 +60 秒，持续 30 分钟。SQL 共用初始化时间；调度/排队超时扫描 PT1S，前端轮询 1 秒。支付截止默认15分钟、等待队列900000ms，数据库扫描PT1M兜底。

## 验收证据地图

| 规划阶段 | 实现及权威验证位置 | 当前结果 |
|---|---|---|
| 1 发号、退款、冻结 | `tikcookie-api/src/main/java/com/dss/order/support/OrderIdGenerator.java`；`OrderCorrectnessTest`、`ProductFreezeTest`、`OrderIdGeneratorTest`、真实IT原ID创建 | 所有新单应用ID、历史不动；到期退款≤；开抢全资料冻结。进程内回拨失败、固定节点/重启时钟前提已说明 |
| 2 启动追加 | `flash/demo/FlashDemoInitializer`、`FlashDemoDataRepository`、`db/flash-demo-append.sql`；`FlashStartupTest`、`OrderJobBarrierTest`、`FlashIsolationIT` | 门禁失败关闭；两轮追加与完整Spring上下文重启保留历史；专用店铺范围检查 |
| 3 商品缓存/调度 | `FlashCacheService`、`ProductServiceImpl`、`FlashLifecycleJob`；`ProductCacheRulesTest`、`FlashCacheIntegrityTest`、`FlashLifecycleJobTest`、真实IT | Redis列表/已知FLASH详情，库存额度分离；首次上架、手动OFF、结束/旧轮；完整性损坏拒绝 |
| 4 Lua | 三份 `resources/lua/flash-*.lua`；`deploy/tests/test_flash_lua.py` | 随机本地Redis 18项通过；库存/额度/绑定/原期限/释放一次/类型与整数/损坏容器回归 |
| 5 异步建单/终止 | `FlashMqConfig/Names/Publisher/Consumer`、`FlashOrderService`、`FlashRequestCoordinator`；`FlashOrderFlowTest`、`FlashMqContractTest`、`FlashCoordinatorTest`、`FlashTransactionBoundaryTest`、IT | 重复消息/迟到/旧轮、提交后Redis故障、不确定提交REQUIRES_NEW核对；synthetic nack保留与单坏请求/首100异常扫描隔离已RED→GREEN |
| 6 生命周期 | `OrderServiceImpl`、`OrderMapper`、两个原订单任务及MQ超时监听；`PaymentDeadlineTest`、`OrderJobBarrierTest`、IT | 锁后支付截止、取消一次提交后补偿，退款/使用不回补；pay/cancel真实竞争、扫描兜底/TTL路由 |
| 7 前端与查询 | `tikcookie-web/src/api/flash.js`、`FlashQueue.vue`、购买/身份/支付helpers，Home/Detail/OrderPay；node tests及浏览器mock | 76项通过build；原key重试/刷新/期限/本人记录、混合身份/多页面/弹窗卸载/刷新重入/发送前换账号/退出登录响应时序已补真实handler/Axios回归；MySQL本人结果通过真实HTTPIT |
| 8 配置/文档/隔离 | `tikcookie-api/docs/FLASH_SALE.md`、接口/需求/配置/部署说明、`.env.example`；`deploy/integration/`、schema/index快照 | 无新增表/字段/索引；搜索MQ未启用，FLASH不经ES；真实Rabbit联调用nonce名称空间已清理 |

所有上述仓库内路径均相对于 `D:/codespace/TikCookie/`；文件本身为当前工作树证据。详细接口、故障和部署说明位于 `D:/codespace/TikCookie/tikcookie-api/docs/FLASH_SALE.md`。

## 已执行验证

- 第1阶段：4项新增断言先失败，最小修复后全量194项、0失败、18跳过。
- 最终普通后端 `mvn -o -B test`：260项、0失败、18真实Live测试跳过。普通测试不导入开发 `.env`，不触发播种。
- 最终 `mvn -o -B verify`：260项、0失败、18跳过，jar/repackage通过。
- 前端 `node --test tests/*.test.js`：76通过；`npm run build`：389 modules、通过；独立规范审查确认导航key修复测试能捕获旧逻辑，Axios传输边界不是仅mock helper。
- 隔离 `FlashIsolationIT`：2项复合场景通过（完整生命周期与实际关闭重开Spring上下文），临时本地MySQL/Redis +已授权真实Rabbit内随机前缀；清理退出0。
- IT包括20并发/10库存、真实HTTP订单字符串和用户隔离、原期限重试、重复MQ、售罄与回补、支付取消竞争、迟到终止、旧轮拒绝、原记录不变、到期退款和schema/index前后相同。
- TTL探针用同样900000ms队列参数与200ms单消息TTL，实际验证死信路由；没有声称已等待实钟15分钟。

日志位于后端 `target/flash-unit-tests.log`、`flash-verify.log`、`flash-native-isolation-it.log`、`flash-native-mq-cleanup.log`，不提交日志/真实连接配置。

## 最终审查与交付边界

- 前后端最后定点复审均通过：后端不确定发布/逐请求隔离/分页/独立事务，前端Axios发送边界与logout/login时序无剩余已确认P1/P2。最终源码已重跑后端test与verify（均260总数/18跳过/0失败）、18Lua、76Node及Vite build；最终隔离IT两个复合场景与MQcleanup再次退出0。
- 按阶段Git提交/合并/推送、服务器部署及旧timer停用未执行；保留现有未提交工作，不未经确认提交它们。
- 图片已复用仓库OSS清单中保留的product_1/2/3真实ObjectKey，三个公开HEAD均200/image/jpeg；其他Bucket需准备相同资源，实机容量和持续运行验收属于部署后工作。
- 单实例、缓存严重损坏停机人工恢复、固定雪花节点和重启时钟前提保留；不增加Outbox/数据库结构/多实例恢复平台。
