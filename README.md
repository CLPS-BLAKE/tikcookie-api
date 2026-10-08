# TikCookie API

TikCookie 的后端仓库，面向 Java 教学成果展示项目，负责业务 API、中间件集成、数据持久化以及共享部署配置。

## 项目定位

- 后端技术方向：Spring Boot
- 数据存储：MySQL 8.0（单机 InnoDB） + MyBatis-Plus
- 缓存：Redis
- 消息队列：RabbitMQ
- 商品检索：Elasticsearch
- 图片存储：阿里云 OSS
- API 文档：Swagger / OpenAPI
- 对应前端仓库：[tikcookie-web](https://github.com/CLPS-BLAKE/tikcookie-web)

> 2026-10-08 更新：用户、店铺、商品、订单、公共基础与文件模块已有实现，收藏和搜索待实现。阿里云 ECS 规格为 2 核 4 GB，四种中间件同驻 node1；后端已部署阿里云，Nginx/前端待部署。后端具体实例和是否同驻 node1 待记录，部署不等于联调通过。见 [实现进度](docs/PROGRESS.md)。
> 同日部署补充：负责人确认 Logstash 已通过 Compose 部署并同步 MySQL → ES，店铺 5/5、商品 30/30，两个索引 mapping 符合配置。Java 搜索 API 和 RabbitMQ 消费同步仍待完成；见 [Logstash 部署记录](docs/LOGSTASH_SYNC.md)。

## 文档入口

- [贡献指南](CONTRIBUTING.md)
- [项目与架构说明](docs/PROJECT_GUIDE.md)
- [实现进度与优先待办](docs/PROGRESS.md)
- [开发、评审与合并流程](docs/WORKFLOW.md)
- [接口协作约定](docs/API_CONTRACT.md)
- [配置与安全约定](docs/CONFIGURATION.md)
- [部署职责与目录约定](docs/DEPLOYMENT.md)
- [测试与验收规范](docs/TESTING.md)
- [版本与发布记录](docs/RELEASES.md)
- [团队职责登记](docs/TEAM.md)
- [安全说明](SECURITY.md)

## 本地构建

后端是单个 Maven 模块，按业务分包：`com.dss.common`、`user`、`shop`、`product`、`order`、`favorite`、`search`、`file`。用户、店铺、商品、订单、公共基础与文件模块已有业务代码；收藏 4 个接口和搜索 2 个接口仍返回 HTTP 501 / 业务码 50100，搜索同步与全量重建方法也仍是桩。

| 项 | 版本 |
| --- | --- |
| JDK | 21 |
| Maven | 3.6.3 及以上（本地用 3.9.9 验证） |
| Spring Boot | 3.5.16 |
| MyBatis-Plus | 3.5.17（`mybatis-plus-spring-boot3-starter` + `mybatis-plus-jsqlparser`） |
| MySQL 驱动 | mysql-connector-j 9.7.0（由 Spring Boot 管理） |
| Elasticsearch 客户端 | 8.18.8，Spring Data Elasticsearch 5.5.13（由 Spring Boot 管理） |
| 阿里云 OSS SDK | 3.18.5 |
| springdoc-openapi | 2.8.17 |

服务端需要 MySQL 8.x 和 Elasticsearch 8.x（与 8.18 客户端兼容），另有 Redis、RabbitMQ 和 OSS。

构建与验证命令（产物是 `target/dss-0.0.1-SNAPSHOT.jar`；CI 使用等效命令 `mvn -B --no-transfer-progress clean verify`）：

```bash
mvn -B clean verify
```

`JAVA_HOME` 必须指向 JDK 21。

当前本地 `src/test` 已有公共契约与上传/消息发布用例，尚未合入核对的远端 main，不能据此声称远端已有测试覆盖。默认用例不依赖中间件；需要真机的两个联调用例
（`OssFileStorageLiveTest`、`SearchSyncPublisherRabbitLiveTest`）在没有 `.env` 时自动跳过，
分层说明见 [测试与验收规范](docs/TESTING.md) 第 2 节。

本地运行：

1. 在空的 MySQL 8.x 库上，用有建表权限的管理账号执行根目录的 `dss-init.sql`（只建 5 张表和索引，不写数据）。
2. 按根目录的 `.env.example` 准备环境变量，复制成 `tikcookie-api/.env`。标"必填"的缺一个，应用就会启动失败。
   `application.yml` 里已经配置 `spring.config.import: optional:file:./.env[.properties]`，**工作目录是 `tikcookie-api`
   时会自动读取 `.env`**（操作系统环境变量优先级更高，服务器/CI 注入真实变量时不受影响）；用 IDEA 运行时注意把工作目录设成模块目录。
3. 启动：`java -jar target/dss-0.0.1-SNAPSHOT.jar`，或在 IDEA 里运行 `com.dss.DssApplication`。
4. 打开 `http://localhost:8080/swagger-ui.html` 查看接口。调用内部接口前，先在 Authorize 里填入 `X-Internal-Key`。
5. MySQL、Redis、RabbitMQ、ES 的连接均指向 `node1` 的可达私网地址或受控入口；实例名不保证 DNS 可解析，不能把本机 localhost 当作云端中间件地址。
6. 订单任务默认关闭，先修复并回归到期退款条件，再设置 `DSS_JOB_ENABLED=true`；搜索重建尚未实现，不应提前启用启动重建。

## 基本协作规则

1. 禁止直接向 `main` 推送业务代码。
2. 从最新 `main` 创建短期任务分支。
3. 所有变更通过 Pull Request 合并。
4. 每个 PR 至少需要 1 名合格评审者批准。
5. 合并前必须通过必需检查并解决全部评审讨论。
6. 统一使用 Squash merge，合并后删除任务分支。
7. 密码、Token、云密钥及真实环境配置不得进入仓库。

## 当前状态

以 2026-10-08 核对的远端 main `af739da` 为基线：32 个既定业务接口中，26 个已有非桩实现、6 个待实现，另有一个公开代理读图接口。收藏、ES 查询、消息消费后的索引同步与全量重建尚未实现。

后端构建 CI 已配置，但不代表运行结果或测试已通过。到期退款条件存在已登记问题。后端已部署阿里云，实际实例/版本待记录；Nginx/前端部署、真实接口接入、全链路验收和自动发布待完成。2C4G ECS 上的四种中间件同驻 node1；见 [部署约定](docs/DEPLOYMENT.md) 与 [进度待办](docs/PROGRESS.md)。

