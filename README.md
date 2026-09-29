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

> 当前仓库处于协作规范初始化阶段。Java、Spring Boot、构建工具及中间件版本将在后端项目骨架合并时固定。

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

## 基本协作规则

1. 禁止直接向 `main` 推送业务代码。
2. 从最新 `main` 创建短期任务分支。
3. 所有变更通过 Pull Request 合并。
4. 每个 PR 至少需要 1 名合格评审者批准。
5. 合并前必须通过必需检查并解决全部评审讨论。
6. 统一使用 Squash merge，合并后删除任务分支。
7. 密码、Token、云密钥及真实环境配置不得进入仓库。

## 当前状态

当前提交只建立仓库文档和协作基础，不代表业务代码、中间件、CI/CD 或云端部署已经完成。

