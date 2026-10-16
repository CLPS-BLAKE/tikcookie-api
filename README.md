# TikCookie API

TikCookie 是团购券教学项目。本仓库提供后端 API，支持浏览商品、验证码登录、普通下单、抢购、模拟支付和用券。它不是商用交易系统。

前端源码、开发与测试入口：[tikcookie-web](https://github.com/CLPS-BLAKE/tikcookie-web)。

业务代码基线为远端 main `e79f3496e6790efc9728a9926b4152b4bd069f16`。已实现、构建通过、部署成功和业务验收分别记录。

## 功能和架构

- 用户：演示验证码、自动注册、登录、退出、昵称和头像。
- 店铺与商品：分类、详情、列表、内部录入和上下架。
- 订单：普通同步下单；抢购通过 Redis 预占、RabbitMQ 异步建单。
- 交易：模拟支付、券码、本人用券、取消、退款和到期任务。
- 配套：收藏、评价、Elasticsearch 搜索、OSS 图片上传和代理读取。

后端是一个 Spring Boot 应用。MySQL 保存业务数据；Redis 保存登录态和抢购状态；RabbitMQ 处理抢购消息。Java 只读 ES，索引由 Logstash JDBC 同步。图片存储使用外部阿里云 OSS。

## 构建与运行

需要 JDK 21 和 Maven。主要依赖为 Spring Boot 3.5.16、MyBatis-Plus 3.5.17、OSS SDK 3.18.5。前端使用自己的仓库与锁文件构建。

在后端仓库根目录执行：

```bash
mvn -B --no-transfer-progress clean verify
```

产物为 `target/dss-0.0.1-SNAPSHOT.jar`。准备 OSS，复制 `.env.example` 为 `.env` 并填值和确认新建环境。Compose 的 MySQL 在空卷首次启动时自动执行 `dss-init.sql`、创建六表和账号，不再手工重复导入。后端从包含 `.env` 的仓库根目录启动：

```bash
java -Dserver.address=127.0.0.1 -jar target/dss-0.0.1-SNAPSHOT.jar
```

应用自动读取 `.env`，进程环境变量优先。Swagger 在启用后可通过受控本机入口 `/swagger-ui.html` 使用。

**抢购启动有副作用**：代码默认启用抢购，每次启用启动会追加一轮演示商品。首次基础检查显式设 `DSS_FLASH_ENABLED=false`，启用演示前阅读抢购说明。`DSS_SEARCH_REBUILD_ON_STARTUP` 必须 false。

## 验证记录

2026-10-16（本机 Windows）：JDK 21 执行上述 Maven 命令，189 项测试，0 失败、0 错误、17 跳过，构建成功；前端 `npm ci`、`npm run build` 成功。Mockito/Byte Buddy 动态代理告警仍在，跳过项不算通过。目标 Linux 完整部署、真实依赖和全链路未验证；Nginx 局部验证见 [TESTING.md](docs/TESTING.md)。

## 部署与文档

单机 Linux 上用 `deploy/compose/middleware.yml` 运行 MySQL、Redis、RabbitMQ、Elasticsearch、Logstash；后端运行 JAR；Nginx 使用 `deploy/nginx/tikcookie.conf` 托管 `/var/www/tikcookie-web/dist` 并转发 API。统一使用后端根目录 `.env`。完整克隆、初始化、构建和启停步骤见 [部署说明](docs/DEPLOYMENT.md)。本地前端开发使用 Vite `/api` 代理连接回环后端，不使用生产 Nginx 代替开发服务器。

| 文档 | 内容 |
| --- | --- |
| [需求文档](docs/需求文档.md) | 目标、规则、验收和用户故事教学附录 |
| [项目指南](docs/PROJECT_GUIDE.md) | 总体架构、数据流和源码入口 |
| [接口文档](docs/接口文档.md) | 路径、鉴权、参数、字段和错误码 |
| [部署说明](docs/DEPLOYMENT.md) | 单机 Linux 可执行步骤 |
| [配置说明](docs/CONFIGURATION.md) | 统一环境变量、OSS 和安全边界 |
| [测试说明](docs/TESTING.md) | 已执行记录、测试分层和核心验证 |
| [抢购技术说明](docs/FLASH_SALE.md) / [使用指南](docs/FLASH_SALE_USER_GUIDE.md) | 抢购流程和故障边界 |
| [发布记录](docs/RELEASES.md) | 基线、剩余验证和发布登记 |
| [团队](docs/TEAM.md) / [协作流程](docs/WORKFLOW.md) | 分工和评审 |
| [贡献指南](CONTRIBUTING.md) / [安全说明](SECURITY.md) | 贡献和敏感信息处理 |
| [历史计划](docs/plan/README.md) | 历史设计与计划 |
