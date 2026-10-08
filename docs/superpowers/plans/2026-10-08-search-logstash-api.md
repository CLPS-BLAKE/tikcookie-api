# Search Logstash API Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline; preserve existing changes. No implementation delegation or automatic commits. Read-only final review follows requesting-code-review.

**Goal:** 实现两个搜索 API，Logstash 同步；仅搜索不使用 RabbitMQ，保留其他功能可复用的通用 MQ 能力。
**Architecture:** ElasticsearchOperations + NativeQuery 查询，FileUrlResolver 映射图片，Spring 默认发布器无操作。
**Tech Stack:** Java21 / Spring Boot3.5 / Spring Data Elasticsearch / JUnit5 / Mockito / MockMvc。

- [x] 添加 SearchServiceImplTest 与 SearchControllerTest，运行确认桩导致失败。
- [x] 实现 searchProducts/searchShops；内部 sync/rebuildAll 明确拒绝。重跑上述测试通过。
- [x] 添加 LogstashOnlyConfigTest：仅关闭搜索拓扑/发布/消费；按用户澄清保留 AMQP 自动配置、公共 JSON/投递回调和 Rabbit 连接参数，先失败后验证。
- [x] 历史真实 Rabbit/OSS 测试增加显式开关，默认不运行。
- [x] 添加显式只读真实 ES 的 SearchApiEsLiveTest，11 项真实测试通过；另有真实日期映射及完整应用通用 MQ 可用但搜索隔离验证。
- [x] 更新环境示例、搜索契约、Logstash 和测试文档，保留既有改动。
- [x] JDK21 clean verify：172 项通过、17 项按开关跳过、0 失败/错误；生成 JAR，git diff --check 无错误。具体记录见 docs/SEARCH_API.md。
