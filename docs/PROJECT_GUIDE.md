# 项目与架构说明

## 1. 项目目标

TikCookie 是一个团购业务方向的 Java 教学展示项目。团队共 5 人，采用前后端分离开发，目标是完成可联调、可部署、可重复演示的业务系统。

后端主要覆盖：

- 登录与身份管理
- 用户、店铺、商品和订单业务
- 商品分类筛选与关键词搜索
- 商品及用户图片管理
- Redis 缓存或登录状态
- Logstash JDBC 已完成的 MySQL → ES 同步；RabbitMQ 既有消息消费仍待完成
- 可通过 Swagger / OpenAPI 验证的接口

最终业务范围以团队任务和验收清单为准。

## 2. 运行位置与目标入口（2026-10-08 更新）

| 位置 | 服务 |
| --- | --- |
| ECS node1（2 核 4 GB，已运行） | MySQL、Redis、Elasticsearch、RabbitMQ |
| 阿里云 ECS（后端已部署） | Spring Boot；具体实例/版本及是否同驻 node1 待记录 |
| 待部署 | Nginx、前端构建物；位置与后端 upstream 待确认 |
| 阿里云 OSS | 商品图片与用户上传图片 |
| 阿里云 ACR | 前端与后端镜像 |

阿里云 ECS 规格为 2 核 4 GB。四种中间件同驻 node1，后端已部署阿里云，但是否与 node1 同机尚未确认；Nginx 和前端尚未部署。目标统一入口不表示现状已通过 Nginx 接入。实际后端实例/版本、网络和端口待记录；资源/持久化/边界见 [部署约定](DEPLOYMENT.md)。

## 3. 中间件业务用途

| 组件 | 业务用途 | 验收方式 |
| --- | --- | --- |
| MySQL + MyBatis-Plus | 用户、店铺、商品和订单数据 | 数据可写入、查询并持久保存 |
| Redis | 登录状态、验证码、频控与错误次数 | 登录链路实际使用 Redis；本期不做热门商品缓存 |
| RabbitMQ | 商品变更消息 | 可观察消息生产、消费与失败处理 |
| Elasticsearch | 商品搜索和分类筛选 | 使用真实索引，商品变化后结果可更新 |
| OSS | 商品与用户图片 | 上传后可在页面访问 |

## 4. 示例业务链路

当前索引链路：商品/店铺变更维护 MySQL 更新时间，Logstash 每分钟读取并覆盖 ES。2026-10-08 负责人确认部署及同步成功，店铺 5/5、商品 30/30，mapping 符合配置；见 [部署记录](LOGSTASH_SYNC.md)。RabbitMQ 发布及消费者入口保留，SearchService 的消费同步、查询和重建仍是桩；Java/API/页面搜索尚待完成，不等于整条用户链路已验收。

Elasticsearch 索引必须能够从 MySQL 重建，不能作为唯一业务数据来源。

## 5. 已经确定的技术决定

骨架已经落地，下面这些不再是待定项（细节见对应文档）：

| 决定 | 结论 | 依据 |
| --- | --- | --- |
| 语言与框架 | JDK 21、Spring Boot 3.5.16、单个 Maven 模块按业务分包 | `pom.xml`、README |
| 模块边界 | `common` 不反向依赖业务包；业务模块通过 Service 接口协作，例如订单调用商品/店铺服务 | 各模块 Service |
| 统一响应 | `{code, msg, data}`，成功 `0`；通用码对应 HTTP 状态，业务码固定 200 | [接口文档](接口文档.md) 1.3 |
| 异常处理 | 全局 `@RestControllerAdvice` 统一转换，不向客户端返回框架异常原文 | `GlobalExceptionHandler` |
| 鉴权 | C 端 Bearer token（Redis 登录态、7 天滑动续期）+ 内部 `X-Internal-Key` | [接口文档](接口文档.md) 1.2 |
| 数据访问 | MySQL 8.x + MyBatis-Plus（分页插件、`createdAt`/`updatedAt` 自动填充） | [中间件配置](中间件配置.md) 3 |
| 中间件版本 | MySQL 8.x、Elasticsearch 8.x（客户端 8.18）、Redis、RabbitMQ、OSS SDK 3.18.5 | README |
| 图片 | 后端生成 ObjectKey 存 OSS，库里只存 fileId，URL 由 `FileUrlResolver` 拼接 | [中间件配置](中间件配置.md) 6 |
| 搜索同步 | Logstash JDBC 已部署并同步；Java 查询/MQ 消费/重建仍是桩，后续明确写入方切换 | [中间件配置](中间件配置.md) 5、[部署记录](LOGSTASH_SYNC.md) |
| 构建与测试命令 | `mvn -B clean verify`（JDK 21）；后端 CI 已配置 | README、[测试与验收规范](TESTING.md) |

## 6. 尚未完成的内容

- 收藏、ES 查询、消息同步索引与全量重建；
- 到期退款条件修复，库存/限购/状态与重复操作回归；
- 端到端联调与页面验收；
- 测试提交及执行证据（本地公共测试未合入核对基线）；
- 应用 Dockerfile/Compose、前端 CI 与自动发布（后端构建 CI 已有）；Logstash Compose 已使用，部署配置待归档到仓库；
- 登记已部署后端的实例/版本；部署 Nginx 和前端，核对 node1 的 2C4G 资源、连接/持久化、健康检查与回退。

详细基线与验收顺序见 [实现进度与待办](PROGRESS.md)。已有实现不替代真实环境验收；待办通过 PR 落地。

