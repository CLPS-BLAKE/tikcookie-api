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

> 后端项目骨架已建立。Java、Spring Boot、构建工具和客户端库的版本见下文"本地构建"；中间件服务端的镜像版本由部署配置锁定。

## 文档入口

- [贡献指南](CONTRIBUTING.md)
- [项目与架构说明](docs/PROJECT_GUIDE.md)
- [开发、评审与合并流程](docs/WORKFLOW.md)
- [接口协作约定](docs/API_CONTRACT.md)
- [配置与安全约定](docs/CONFIGURATION.md)
- [部署职责与目录约定](docs/DEPLOYMENT.md)
- [测试与验收规范](docs/TESTING.md)
- [版本与发布记录](docs/RELEASES.md)
- [团队职责登记](docs/TEAM.md)
- [安全说明](SECURITY.md)

## 本地构建

后端是单个 Maven 模块，按业务分包：`com.dss.common`、`user`、`shop`、`product`、`order`、`favorite`、`search`、`file`。目前业务方法都是桩，调用返回 HTTP 501 / 业务码 50100。

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

构建命令（依次编译、单元测试、打包，产物是 `target/dss-0.0.1-SNAPSHOT.jar`；CI 使用同一条命令）：

```bash
mvn -B clean package
```

`src/test` 目前还是空的，测试阶段会显示 `No tests to run.`。

本地运行：

1. 在空的 MySQL 8.x 库上，用有建表权限的管理账号执行根目录的 `dss-init.sql`（只建 5 张表和索引，不写数据）。
2. 按根目录的 `.env.example` 准备环境变量。标"必填"的缺一个，应用就会启动失败。Spring Boot 不会自动读取 `.env`：Linux / macOS 可以先执行 `set -a; . ./.env; set +a`；Windows 在 IDEA 运行配置的"环境变量"里填写。
3. 启动：`java -jar target/dss-0.0.1-SNAPSHOT.jar`，或在 IDEA 里运行 `com.dss.DssApplication`。
4. 打开 `http://localhost:8080/swagger-ui.html` 查看接口。调用内部接口前，先在 Authorize 里填入 `X-Internal-Key`。

## 基本协作规则

1. 禁止直接向 `main` 推送业务代码。
2. 从最新 `main` 创建短期任务分支。
3. 所有变更通过 Pull Request 合并。
4. 每个 PR 至少需要 1 名合格评审者批准。
5. 合并前必须通过必需检查并解决全部评审讨论。
6. 统一使用 Squash merge，合并后删除任务分支。
7. 密码、Token、云密钥及真实环境配置不得进入仓库。

## 当前状态

已有仓库文档、协作基础和后端项目骨架（接口、配置和表结构已定义，业务方法仍是桩）。业务实现、中间件联调、CI/CD 和云端部署都还没有完成。

