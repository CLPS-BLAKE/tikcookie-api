# 部署职责与目录约定

## 1. 已确认部署事实与目标方案（2026-10-08）

部署负责人已确认 ECS 规格为 **2 核 4 GB（2C4G）**，MySQL、Redis、Elasticsearch、RabbitMQ 同驻 node1；**后端已部署在阿里云，Nginx 和前端等待部署**。2026-10-08 补充确认：服务器 `/opt/tikcookie-data` 已使用 Compose 部署 Logstash 并完成 MySQL → ES 同步，店铺 5/5、商品 30/30，两个索引的 mapping 符合配置。后端具体实例、是否同驻 node1、实际部署版本/运行方式/端口待记录；中间件 Compose 不证明应用也用 Compose，Kubernetes 不作为本期验收要求。

| 位置 | 服务与确认范围 |
| --- | --- |
| ECS node1（2 核 4 GB） | MySQL、Redis、Elasticsearch、RabbitMQ 已运行；连接/持久化/业务验收待记录 |
| 阿里云 ECS（后端已部署） | Spring Boot；实例、部署版本及是否同驻 node1 待确认 |
| 待部署 | Nginx、前端构建物；目标位置和后端 upstream 待确认 |
| 阿里云 OSS | 演示图片对象存储 |

目标发布链路：前后端独立构建镜像并推送 ACR，可信 main 工作流通过云助手调用部署脚本。后端构建 CI 已有，应用 Dockerfile、Compose 和自动发布尚未落地；Logstash 的 Dockerfile/Compose 已在部署目录使用，不代表合并即发布。进度见 [PROGRESS.md](PROGRESS.md)，配置规则、验收证据与维护边界见 [LOGSTASH_SYNC.md](LOGSTASH_SYNC.md)。

## 2. 本仓库的部署职责

整体 Compose 配置、共享部署脚本和环境说明统一放在后端仓库：

```text
deploy/
├── compose/
├── env/
├── scripts/
└── README.md
```

上述仓库目录尚未落地；已使用的 Logstash 配置源文件在本地 `D:\desktop\fsdownload`，服务器运行目录为 `/opt/tikcookie-data`，后续将不含凭据的部署配置归档至 `deploy/`。接入现有中间件先登记、备份，不重新初始化业务库，不改变原 Compose 项目名，不执行 `down -v`。真实凭据不进入仓库。

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
- Logstash 每分钟增量覆盖，首次无游标时全量读取；两个游标及持久队列保存在 `logstash_data`。物理删除不自动清理 ES，须单独维护；`updated_at` 必须随变更更新。
- `SearchService.rebuildAll` 仍是桩，保持 `DSS_SEARCH_REBUILD_ON_STARTUP=false`。维护时可按部署说明重置 JDBC 游标全量覆盖，但这不是删除并重建索引，故障/重启恢复证据仍待补录。
- 演示数据应可重复导入并覆盖主要业务场景。

原四种中间件共享 node1 的 **2 核 CPU、4 GB 内存**，新增 Logstash 也须计入部署主机预算；交付配置限额 768 MiB、JVM 堆 256 MiB，实机限制待登记。记录 ES 堆、MySQL 缓冲池/连接、Redis、MQ 积压、CPU、队列与磁盘余量；若后端同机也计入 Java 进程。部署和同步成功不是容量保证，仍须独立保存 MySQL 备份并验证恢复步骤。

## 5. 网络边界

以下是目标访问边界；Nginx 尚未部署，不能据此声称后端当前已通过 Nginx 接入。部署前核对现有后端入口并限制访问来源，后续按实际 upstream 对接，不擅自改变现有安全组。

- 公网仅开放 Nginx 所需的 80/443。
- SSH 仅允许可信来源访问。
- Spring Boot 经 Nginx `/api` 转发，不直接暴露公网。
- node1 的 MySQL、Redis、RabbitMQ、Elasticsearch 只允许受控应用网络访问，不开放公网业务/管理端口；本地开发使用受控 VPN/隧道或临时来源白名单，不放行全网。
- 应用若位于其他主机，通过受控私网连接 node1；若同驻 node1，根据实际宿主机/容器网络配置，不能默认容器 localhost 指向中间件。
- RabbitMQ 管理台和 ES 管理入口仅管理员受控访问；具体端口及入口由部署方记录。

## 6. 后端部署登记与前端/Nginx 部署待办

- ECS 规格已确认为 2 核 4 GB；补登记 node1 系统、地域/VPC、受控地址、运行方式、端口及持久化路径
- 记录已部署后端的实例、提交/制品版本、启动方式、健康检查及是否同驻 node1
- 确定 Nginx/前端位置并部署，配置实际后端 upstream；不要假定 localhost 指向后端
- ACR 镜像仓库地址
- OSS Bucket 和访问方式
- 各运行时及中间件版本
- 健康检查端点
- 演示入口、域名和 HTTPS 方案
- 备份、重建和回退验证步骤

连接变量统一对应 node1：`DSS_MYSQL_HOST`、`DSS_REDIS_HOST`、`DSS_RABBIT_HOST`、`DSS_ES_URIS`。填写可解析的受控地址及实际端口，不直接把实例名当作已配置的 DNS；不在文档或仓库记录真实凭据。

