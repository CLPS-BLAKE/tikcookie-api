# 接口协作约定

## 抢购商品详情中的个人购买额度（2026-10-13）

GET /api/v1/products/{productId} 保持游客可访问；携带 Bearer token 时先验证真实登录态。
用户 ID 只取认证上下文，不接收查询参数或商品缓存中的用户身份。无效/过期 token 返回 401。
公开商品查询响应设置 Cache-Control: private, no-store，防止个人额度被共享缓存复用。

已登录用户查询 FLASH 商品时，除原有 limitPerUser 外还返回：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| purchaseCount | int / null | 当前用户 Redis 已占用额度，含排队预占及未取消的购买 |
| remainingQuota | int / null | max(0, limitPerUser - purchaseCount) |
| purchaseLimitReached | boolean / null | purchaseCount >= limitPerUser |

游客与 NORMAL 商品的个人额度字段为 null；查询不会改变额度，不将个人字段写入共享商品缓存。
达到个人购买上限仍成功返回商品详情，抢购列表也不按个人额度过滤。
此数据只是查询时快照；真正下单仍由现有 Redis Lua 原子校验决定。

前端进入已达上限的详情时，仅提示“限购【N】件，您已达到购买上限”（N 为真实 limitPerUser）。
两个购买按钮灰显但可点击查看相同提示，不发出新下单请求；下单期间收到 103011 时也切换为此状态。
未完成商品/额度查询前不能触发购买。网络错误保留原请求键重试语义，不能误判为限购。
无需数据库迁移；部署时先发布后端新增返回字段，再发布前端。


> 2026-10-13 秒杀改造更新：普通订单同步、FLASH Redis 预占 + MQ 异步建单，所有新订单应用雪花 ID；新增本人建单结果接口。开抢后全资料冻结；启动追加新轮，不清历史。最新接口/配置/部署/测试及单实例故障边界以 [FLASH_SALE.md](FLASH_SALE.md) 为准；下文旧日期部署记录保留为历史，不能据其推断新分支已发布。

## 1. 基本约定

- 浏览器统一通过 `/api` 访问业务接口。
- OpenAPI 文档必须与实际实现同步。
- 接口路径、HTTP 方法、请求字段、响应字段、错误码和认证方式必须明确。
- 时间、金额、分页和枚举字段必须采用统一格式。

## 2. 统一响应结构

所有接口都返回同一个结构，字段名固定为 `code` / `msg` / `data`（见《接口文档》1.3）：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {}
}
```

- `code` 是整数：`0` 表示成功；通用码（`40000`、`40100`、`40300`、`40400`、`50000`、`50100`）与 HTTP 状态一一对应；
  各模块业务码是 6 位（`1` + 2 位模块号 + 3 位序号），业务码的 HTTP 状态**一律 200**。
- 没有数据时 `data` 输出 `null`，字段本身不省略。
- 错误响应至少区分：参数错误、未登录、无权限、资源不存在、业务冲突和服务异常。
  不得把内部堆栈、密码、连接信息或框架异常原文返回给客户端（`code`/`msg` 用固定文案，细节只进服务端日志）。

## 3. 分页、时间与金额

- 分页请求：`page` 从 1 开始、默认 1；`size` 默认 10、最大 50（`PageQuery`）。
- 分页响应：`{list, total, page, size}`（`PageResult`）。
- 时间：`LocalDateTime` 统一输出 `yyyy-MM-dd HH:mm:ss`，时区东八区（`JacksonConfig` + `spring.jackson.time-zone: GMT+8`，
  启动类把 JVM 默认时区固定为 `Asia/Shanghai`，与 JDBC 的 `connectionTimeZone` 一致）。
- 金额：用整数分，避免二进制浮点计算。
- 业务 ID：在 DTO / VO 里直接声明为 `String`（数据库是 `bigint`），不做全局 Long → String 转换。
- 枚举值在 OpenAPI 中列出并说明含义。

## 4. 变更流程

接口变更 PR 应写明：

- 变更前后的字段或行为
- 是否向后兼容
- 对应前端页面和后端模块
- 联调与发布顺序
- 必要的迁移或回退方案

## 5. 图片接口

- 图片存储由后端与阿里云 OSS 交互，客户端上传不直接接触 OSS。
- 客户端不得获得长期有效的 OSS AccessKey；密钥只存在于后端配置。
- **Bucket 已开公共读**：`url` 指向 OSS 公共域名，游客和登录用户都能把它直接放进 `<img src>` 看到图片，
  图片流量不经过后端。后端仍保留公开的代理读图接口 `GET /api/v1/images/{fileId}`（带长缓存与 ETag），
  作为 Bucket 收紧为私有读时的备用路径；`dss.file.base-url` 指向哪个前缀由部署方决定。
- 只接受 jpg / png / webp，单张 5MB（框架请求上限 10MB）；类型按文件头魔数判定，不信客户端声明的 Content-Type。
- 上传接口返回 `{fileId, url}`：`fileId` 是 OSS ObjectKey（`group1/M00/00/00/{32 位十六进制 uuid}.{后缀}`，后端生成，
  不用原始文件名），数据库只保存 `fileId`；`url` = 图片公共前缀 + `"/"` + `fileId`，
  由 `FileUrlResolver` 统一拼接：配了 `dss.file.base-url` 就用它（OSS 公共域名 / CNAME 域名 / 后端读图接口都行），
  没配时按 `dss.file.oss.endpoint` + `bucket` 自动推导 `https://{bucket}.{endpoint-host}`。
- 业务接口只接受本服务生成的 `fileId`：写库前用 `ObjectKeys.requireValid(...)` 校验，
  拒绝任意外部 URL、本地路径和路径穿越（非法时 `400` / `40000`）。
- 读图失败（fileId 不合法或对象不存在）返回 `404` / `40400`；上传的业务错误按文件模块码返回
  （`106001`~`106004`，HTTP 200）。

