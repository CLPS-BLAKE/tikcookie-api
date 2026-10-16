# 配置说明

代码配置以 `src/main/resources/application.yml` 和 `DssProperties` 为准，基线为 main `e79f349`。示例清单见根目录 [.env.example](../.env.example)。单机部署使用同一份示例，不另建中间件环境文件。

## 统一环境文件

应用将工作目录下的 `.env` 当作 properties 读取，操作系统环境变量优先。统一从仓库根目录运行；Compose 显式使用 `--env-file .env`。容器与后端进程不会自动共享同一套网络地址。

只用简单的 `KEY=value`，不写 `export`、命令替换或 shell 脚本。不要假定引号和变量展开在 Spring、Compose、shell 中含义相同。真实凭据由部署方注入，`.env` 不提交、不打印到日志或 PR。

## Compose 初始化和运行变量

- 先 `cp .env.example .env`，填值后 `chmod 600 .env`。只用于全新环境时，将 `DSS_NEW_DATABASE_CONFIRM` 设为 `CREATE_NEW_DSS`。
- `DSS_MYSQL_ROOT_PASSWORD` 是管理口令；`DSS_MYSQL_PASSWORD` 对应固定应用账号 dss_app；`DSS_LOGSTASH_MYSQL_PASSWORD` 对应固定只读账号 dss_logstash。后二者必须 32–128 位十六进制，用 `openssl rand -hex 32` 分别生成。
- MySQL 库固定为 dss。空卷首次启动由 mysql-init.sh 自动导入六表、创建账号；不要额外手工导入或重复建用户。已有卷不会重新初始化。
- Compose 使用 mysql、redis、rabbitmq、es、logstash 服务名。宿主机 JAR 的 DSS 连接地址为回环地址，不使用容器服务名。
- `SERVER_ADDRESS=127.0.0.1`、`JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Shanghai` 由进程环境或 systemd EnvironmentFile 加载。只有 Spring 读取 `.env`，并不会让 JVM 自动读取 JAVA_TOOL_OPTIONS。
- 示例将 profile 设为 prod、Swagger 和抢购设为 false，区别于代码默认值。启动端口 8080 与 Nginx 保持一致。
- 本地 ES 使用 elastic 账号和认证 HTTP，不是生产最小权限/TLS 配置。Nginx HTTP 仅用于可信网络演示，公网部署另配 HTTPS。

## 应用变量

| 分组 | 变量和说明 |
| --- | --- |
| 应用 | `DSS_PROFILE` 默认 dev，服务器用 prod；`DSS_SERVER_PORT` 默认 8080；`DSS_SWAGGER_ENABLED` 默认 true |
| MySQL | `DSS_MYSQL_HOST`、`DSS_MYSQL_USERNAME`、`DSS_MYSQL_PASSWORD` 必填；端口默认 3306，`DSS_MYSQL_DATABASE` 默认 dss |
| Redis | `DSS_REDIS_HOST`、`DSS_REDIS_PASSWORD` 必填；`DSS_REDIS_PORT` 默认 6379 |
| RabbitMQ | `DSS_RABBIT_HOST`、`DSS_RABBIT_USERNAME`、`DSS_RABBIT_PASSWORD` 必填；端口默认 5672，vhost 默认 `/dss` |
| ES | `DSS_ES_URIS` 必填；`DSS_ES_USERNAME`、`DSS_ES_PASSWORD` 按认证方式填写 |
| 内部鉴权 | `DSS_INTERNAL_KEY` 为随机长口令；为空时内部接口拒绝访问 |
| 登录 | `DSS_AUTH_TOKEN_TTL` 默认 P7D，鉴权访问滑动续期 |
| 图片 | `DSS_FILE_BASE_URL` 控制读取前缀；`DSS_FILE_MAX_SIZE` 默认 5MB；`DSS_FILE_ALLOWED_TYPES` 默认 jpeg/png/webp 的 MIME 列表 |
| 订单 | `DSS_ORDER_PAY_TIMEOUT_MINUTES` 默认 15 |
| 扫描任务 | `DSS_JOB_ENABLED` 在 application.yml 默认 true；两项扫描间隔默认 PT1M |
| 抢购 | `DSS_FLASH_ENABLED` 默认 true；`DSS_FLASH_MQ_PREFIX` 默认 dss.flash；生命周期和请求终止扫描间隔默认 PT1S |
| 搜索维护 | `DSS_SEARCH_REBUILD_ON_STARTUP` 必须 false，true 会拒绝启动，不执行索引重建 |

扫描间隔变量为 `DSS_JOB_ORDER_TIMEOUT_INTERVAL`、`DSS_JOB_VOUCHER_EXPIRE_INTERVAL`、`DSS_FLASH_LIFECYCLE_INTERVAL`、`DSS_FLASH_REQUEST_TIMEOUT_INTERVAL`。时长使用 ISO-8601。

启用抢购会在每次启动时追加演示轮次。测试隔离时显式关闭抢购和任务。prod profile 主要改变日志级别，不会自动关闭演示验证码或 Swagger；演示验证码会在响应中返回，不能作为真实短信认证部署。

## OSS 资源准备

1. 用户自行在阿里云控制台开通 OSS。Compose 不创建云资源。先确认存储、请求和公网流出流量费用；代理读图还占后端带宽。核对主机网络和 Bucket 地域，内网 endpoint 不能直接给外部浏览器使用。
2. 由资源所有者创建专用演示 Bucket，选择适合部署位置的地域和标准存储，初始保持私有及阻止公共访问。只存可公开展示的演示图片。
3. 创建专用 RAM 用户和编程访问密钥，不用主账号 AccessKey。Bucket 创建、ACL 设置和控制台预置图片由资源管理身份操作，不为应用扩大权限。
4. 给该 RAM 用户绑定下列自定义策略。将 `YOUR_BUCKET` 替换为实际 Bucket 名，只允许目标前缀的上传和读取；不授予 FullAccess、列举或管理 Bucket 权限。

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["oss:PutObject", "oss:GetObject"],
      "Resource": ["acs:oss:*:*:YOUR_BUCKET/group1/M00/00/00/*"]
    }
  ]
}
```

确需清理对象时，才在相同 Resource 下增加 `oss:DeleteObject`，不要扩大到其他 Bucket 或目录。

5. 将下表的 endpoint、bucket、region 和完整凭据填入受控 `.env`；密钥不进入前端、Nginx、URL 或仓库。
6. 选择读取方式：初始采用私有 Bucket + 后端公开代理，`DSS_FILE_BASE_URL=/api/v1/images`；若确需 OSS 直接公共读取，再显式配置演示 Bucket 为公共只读，并填写浏览器可访问的公共域名前缀。公共读取涉及阻止公共访问设置时先评估权限，不开放匿名写入。
7. 重启后端后验证上传、对象存在和读取，再把 fileId 绑定到店铺/商品。具体命令见 [准备演示数据](DEPLOYMENT.md#10-准备演示数据)。

## OSS 必填配置

| 变量 | 格式和含义 |
| --- | --- |
| `DSS_OSS_ENDPOINT` | 如 `https://oss-cn-hangzhou.aliyuncs.com`，对应 Bucket 所在地域的服务端点 |
| `DSS_OSS_BUCKET` | 已创建的演示图片 Bucket 名称，不是 URL |
| `DSS_OSS_REGION` | 如 `cn-hangzhou`，与 Bucket 一致；当前客户端构造使用 endpoint，region 配置不代替端点选择 |
| `DSS_OSS_ACCESS_KEY_ID` / `DSS_OSS_ACCESS_KEY_SECRET` | 两项同时非空时，代码使用这组静态配置凭据 |

两项 DSS 凭据未同时配置时，`OssConfig` 使用 SDK 的环境变量凭据提供器，读取进程环境 `OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET` 和可选 `OSS_SESSION_TOKEN`。这不是自动发现 ECS 实例角色的实现。只把 `OSS_*` 写入 Spring 读取的 `.env`，不等于已注入 SDK 所需的进程环境。

按现有代码，使用受控 `.env` 的完整 DSS 静态凭据对，或由进程管理器注入完整 OSS 环境凭据。不要混填。临时凭据的刷新由外部部署机制负责，不能据此声称支持自动续期。

使用专用最小权限身份，只允许所需 Bucket/对象前缀的写入和读取；确需删除时单独授予删除权限。禁止匿名写、禁止向浏览器下发长期 AccessKey。客户端初始化成功不证明 Bucket、网络或权限可用。

## 两种读图方式

**OSS 公共读**：由部署方明确开启对象公共读取。`DSS_FILE_BASE_URL` 填公共 OSS 域名或已配置的域名；留空会按 endpoint 和 bucket 推导 URL。若 endpoint 是内网地址，不要直接将其作为浏览器读取地址。

**后端代理读**：Bucket 保持私有，前缀设为 `https://<演示域名>/api/v1/images`，本机可用 `http://127.0.0.1:8080/api/v1/images`。后端凭据读取对象，再返回图片流。代理接口本身公开，带缓存和 ETag；这不提供用户级图片访问控制。

两种方式都只适合可公开展示的图片。Bucket ACL 和域名按所选读取方式配置。

## 上传验证

1. 准备小于 5 MB 的真实 jpg/png/webp 文件。
2. 带 Bearer token 调 `POST /api/v1/files/images`；或带内部口令调 `POST /api/v1/internal/files/images`，multipart 字段名为 `file`。
3. 检查 `code=0`、`data.fileId` 和 `data.url`，确认 Bucket 中存在对象。
4. 浏览器打开返回 URL。代理模式再检查 ETag/304；公共读模式确认不需要后端凭据。
5. 将 fileId 写入头像或商品，再查业务详情，确认 URL 可见。上传成功不等于业务绑定成功。
6. 检查无凭据、伪装扩展名、超限文件的失败结果。上传失败码为 `106004`；代理读取失败按 `40400` 返回，原因需查脱敏日志。

不要把真实 token、内部口令、OSS 密钥、用户图片或完整环境文件放入验证记录。
