# 项目与架构说明

## 1. 项目目标

TikCookie 是一个团购业务方向的 Java 教学展示项目。团队共 5 人，采用前后端分离开发，目标是完成可联调、可部署、可重复演示的业务系统。

后端主要覆盖：

- 登录与身份管理
- 用户、店铺、商品和订单业务
- 商品分类筛选与关键词搜索
- 商品及用户图片管理
- Redis 登录态与验证码
- RabbitMQ 驱动的搜索索引更新
- 可通过 Swagger / OpenAPI 验证的接口

最终业务范围以团队任务和验收清单为准。

## 2. 目标运行架构

| 位置 | 服务 |
| --- | --- |
| ECS A：demo-app | Nginx、前端、Spring Boot、Redis、RabbitMQ |
| ECS B：demo-data | MySQL、Elasticsearch |
| 阿里云 OSS | 商品图片与用户上传图片 |
| 阿里云 ACR | 前端与后端镜像 |

两台 ECS 位于同一地域和 VPC。业务入口由 Nginx 统一提供，后端通过内网访问数据节点。

## 3. 中间件业务用途

| 组件 | 业务用途 | 验收方式 |
| --- | --- | --- |
| MySQL + MyBatis-Plus | 用户、店铺、商品和订单数据 | 数据可写入、查询并持久保存 |
| Redis | 验证码、发送间隔、登录 token（登录态） | 至少一个真实功能使用 Redis |
| RabbitMQ | 店铺 / 商品变更后同步搜索索引（shop.changed / product.changed） | 可观察消息生产、消费与失败处理 |
| Elasticsearch | 商品和店铺关键词搜索、分类筛选 | 使用真实索引，商品变化后结果可更新 |
| OSS | 商品与用户图片 | 上传后可在页面访问 |

## 4. 示例业务链路

商品新增或更新后，后端写入 MySQL，并发送 RabbitMQ 消息；消费者根据消息更新 Elasticsearch 索引。搜索接口查询 Elasticsearch，业务详情仍以 MySQL 为准。

Elasticsearch 索引必须能够从 MySQL 重建，不能作为唯一业务数据来源。

## 5. 尚待项目骨架确定的内容

- Java、Spring Boot 与 Maven/Gradle 版本
- 代码目录、模块边界和异常响应结构
- MySQL 表结构与索引（含 MyBatis-Plus 实体映射约定）
- 各中间件固定版本
- 本地启动、检查、测试和构建命令
- Dockerfile、Compose 与 CI/CD 工作流

这些决定需要通过 PR 落地并更新 README 与相关文档。

