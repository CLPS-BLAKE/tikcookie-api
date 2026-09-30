# 部署职责与目录约定

## 1. 目标方案

项目使用两台 Ubuntu 24.04 LTS ECS，并以 Docker Compose 管理服务，不将 Kubernetes 作为本次验收要求。

| 位置 | 目标服务 |
| --- | --- |
| ECS A：demo-app | Nginx、前端、Spring Boot、Redis、RabbitMQ |
| ECS B：demo-data | MySQL、Elasticsearch |

前端与后端分别构建镜像并推送至阿里云 ACR。合并 `main` 后，由 GitHub Actions 构建并调用阿里云云助手执行部署脚本。

## 2. 本仓库的部署职责

整体 Compose 配置、共享部署脚本和环境说明统一放在后端仓库：

```text
deploy/
├── compose/
├── env/
├── scripts/
└── README.md
```

上述目录在实际服务版本确定后创建。真实凭据不进入仓库。

## 3. 发布原则

- 前后端分别构建和发布，只更新各自应用服务。
- 镜像使用提交 SHA 标识，避免只使用 `latest`。
- 数据库、缓存和消息服务不随应用发布反复重建。
- 共享环境更新必须串行执行。
- 健康检查通过后才确认发布成功。
- 保留上一成功应用镜像，以便失败后恢复。

## 4. 数据与持久化

- MySQL、Elasticsearch、RabbitMQ 以及需要保留状态的 Redis 使用持久化卷。
- MySQL 是业务数据来源。
- Elasticsearch 索引可从 MySQL 重建。
- 演示数据应可重复导入并覆盖主要业务场景。

## 5. 网络边界

- 公网仅开放 Nginx 所需的 80/443。
- SSH 仅允许可信来源访问。
- Spring Boot 经 Nginx `/api` 转发，不直接暴露公网。
- MySQL 和 Elasticsearch 仅允许 ECS A 通过内网访问。
- Redis、RabbitMQ 仅供应用网络访问。

## 6. 首次部署前待确定项

- 两台 ECS 的实例、内网地址和 VPC
- ACR 镜像仓库地址
- OSS Bucket 和访问方式
- 各运行时及中间件版本
- 健康检查端点
- 演示入口、域名和 HTTPS 方案
- 备份、重建和回退验证步骤

