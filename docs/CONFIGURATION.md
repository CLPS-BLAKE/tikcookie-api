# 配置与安全约定

## 1. 配置分层

- 仓库：仅保存无敏感信息的默认配置和示例。
- 本地开发：使用未纳入版本控制的环境文件或 IDE 配置。
- CI/CD：使用 GitHub Actions Secrets 或适用的环境保护机制。
- 服务器：使用受控环境文件或凭据管理服务。

## 2. 禁止提交的内容

- 数据库和中间件真实密码
- GitHub Token、阿里云 AccessKey、OSS 密钥
- ACR 登录密码、SSH 私钥
- 含用户数据的数据库导出、日志或截图
- 生产 `.env` 文件

## 3. 配置示例要求

项目骨架应提供 `.env.example` 或等效示例，并为每项说明：

- 用途
- 是否必填
- 示例格式
- 默认值和适用环境
- 是否敏感

示例值必须使用明显占位符，不得复制真实凭据。

2026-10-08 已确认 ECS 为 2 核 4 GB，原四种中间件同驻 node1；后端已部署阿里云，Nginx/前端待部署。Logstash 已在 `/opt/tikcookie-data` 使用 Compose 部署并同步，详情见 [LOGSTASH_SYNC.md](LOGSTASH_SYNC.md)。后端实例、是否同驻 node1 及容器网络待记录。应用的 DSS_MYSQL_HOST、DSS_REDIS_HOST、DSS_RABBIT_HOST、DSS_ES_URIS 使用受控可达地址/实际端口，不能照搬 Logstash 容器内的 mysql、es 服务名或服务器 localhost。搜索链路不使用 RabbitMQ，但保留通用 MQ 能力和 DSS_RABBIT_* 配置供其他功能使用，不启动搜索 MQ 消费者。真实配置仅存受控环境文件。

Logstash Compose 复用 DSS_MYSQL_USERNAME/PASSWORD，容器内 MySQL/ES 分别使用 mysql:3306、es:9200。MYSQL_ROOT_PASSWORD、ELASTIC_PASSWORD 为 Compose 必需变量，必须对应已有实例真实口令；改变环境变量不会重置已有卷中的密码。保持 DSS_SEARCH_REBUILD_ON_STARTUP=false，Java 重建明确禁止。部署者已确认服务器可用，本地配置文件是否同步补齐不能据此推断；本次不修改真实 .env。

## 4. 建议配置分组

- 应用端口与运行环境
- MySQL 连接（地址、端口、库名、账号、口令、JDBC 参数）
- Redis 连接
- RabbitMQ 通用连接和投递配置（其他业务使用；搜索不使用）
- Elasticsearch 连接
- OSS Bucket、地域与凭据引用
- 日志级别
- 健康检查和监控参数

## 5. 日志要求

日志不得输出密码、Token、Authorization 请求头、完整 Cookie 或 OSS 密钥。涉及个人数据时应脱敏。

