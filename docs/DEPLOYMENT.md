# 单机 Linux 部署

业务代码基线为 main `e79f349`。方案为五个中间件容器、一个后端 JAR、Nginx 前端 dist。构建已有通过记录；以下 Linux 操作、真实中间件、OSS 和完整业务链路仍需实机验证。

## 1. 环境前提

以下命令使用 Bash、systemd 和 Debian/Ubuntu 风格的 Nginx 目录。在有 sudo 权限的非 root 部署账号下执行。需要：

- Git、curl、OpenSSL、JDK 21、Maven；`JAVA_HOME` 指向 JDK 21。
- Docker Engine 和 Compose V2，部署账号可运行 Docker 命令。
- Node.js 满足前端 `package.json` 的 engines：`^22.18.0 || >=24.12.0`，并安装 npm。
- Nginx；主机可以访问 GitHub、Maven/npm 仓库、镜像源和 OSS。
- 足够的内存、磁盘和 CPU。五个中间件内存限额合计约 5.75 GiB，还须为后端和系统留余量；2 核 4 GB 不满足这组资源预算。
- 仅在可信演示网络开放入口；公网使用前先配置域名、HTTPS 和访问限制。验证码接口直接返回演示验证码，不是真短信认证。

```bash
java -version
mvn -version
node --version
npm --version
docker version
docker compose version
nginx -v
timedatectl status
```

业务时间统一为 Asia/Shanghai。专用新主机先设置时区和 ES 映射上限；已有主机先评估这些系统级变更。文件描述符、容器内存上限和 JVM 堆见配置及下表。不要在未备份的现有业务主机上覆盖容器、卷或 Nginx 站点。

```bash
sudo timedatectl set-timezone Asia/Shanghai
printf '%s\n' 'vm.max_map_count=1048576' | sudo tee /etc/sysctl.d/99-tikcookie-es.conf > /dev/null
sudo sysctl -p /etc/sysctl.d/99-tikcookie-es.conf
```

## 2. 获取两个仓库

新主机按下列目录安装。已有工作目录先核对分支和未提交修改，不重复 clone 或重置。

```bash
DEPLOY_USER="$(id -un)"
test "$DEPLOY_USER" != root
sudo install -d -m 755 -o "$DEPLOY_USER" -g "$(id -gn)" /opt/tikcookie
cd /opt/tikcookie
git clone --branch main https://github.com/CLPS-BLAKE/tikcookie-api.git
git clone --branch main https://github.com/CLPS-BLAKE/tikcookie-web.git
API=/opt/tikcookie/tikcookie-api
WEB=/opt/tikcookie/tikcookie-web
git -C "$API" rev-parse HEAD
git -C "$WEB" rev-parse HEAD
```

记录实际部署的两个提交 SHA。本文的业务基线不是已部署版本号。

## 3. 准备统一环境文件

```bash
cd "$API"
umask 077
test ! -e .env && cp .env.example .env
chmod 600 .env
vi .env
```

在编辑器中填好所有必填项，不保留占位符。不要覆盖已有 `.env`，也不要将其提交、贴到 PR 或写入构建日志。

- 宿主机 JAR 连接 `127.0.0.1` 的 MySQL、Redis、RabbitMQ、ES 映射端口；容器内部地址由 Compose 配置。
- 设置 MySQL 管理/应用/Logstash 密码、Redis 密码、RabbitMQ 账号密码和 ES 密码。应用不用 root，Logstash 使用只读账号。
- 本方案仅用于新环境：审查 `dss-init.sql` 后，将 `DSS_NEW_DATABASE_CONFIRM=CREATE_NEW_DSS`。库名固定 `dss`，账号固定 `dss_app` 和只读 `dss_logstash`；现有卷不能靠改变量迁移或重置密码。
- 填写 OSS endpoint、bucket、region 和完整凭据，选定图片读图前缀，见 [配置说明](CONFIGURATION.md)。
- 设置 `DSS_PROFILE=prod`、`DSS_SWAGGER_ENABLED=false`、`DSS_FLASH_ENABLED=false`、`DSS_SEARCH_REBUILD_ON_STARTUP=false`；保持后端端口 8080 与 Nginx upstream 一致。
- 订单扫描按验收需要设置 `DSS_JOB_ENABLED`。启用抢购前核对演示店铺、目标 Bucket 图片和旧 timer，见 [FLASH_SALE.md](FLASH_SALE.md)。

用 `openssl rand -hex 32` 分别生成独立口令。`DSS_MYSQL_PASSWORD` 和 `DSS_LOGSTASH_MYSQL_PASSWORD` 必须为 32–128 位十六进制，初始化脚本会检查。其他本地口令也使用十六进制，减少解析歧义。不要 `source .env`。

定义一个后续命令使用的 Bash 函数，每次新开终端重新执行：

```bash
API=/opt/tikcookie/tikcookie-api
WEB=/opt/tikcookie/tikcookie-web
dc() {
  docker compose --env-file "$API/.env" \
    -f "$API/deploy/compose/middleware.yml" "$@"
}
dc config --quiet
```

解析失败先修配置，不启动。不要用不带 `--quiet` 的配置输出公开真实口令。

## 4. 拉取、构建并启动四个基础服务

固定版本与资源如下。Logstash 自定义镜像包含固定 Connector/J 8.4.0。

| 服务名 | 镜像版本 | 内存上限 | JVM 堆或主要限制 |
| --- | --- | --- | --- |
| mysql | mysql:8.4.4 | 1 GiB | 缓冲池 256 MiB，连接上限 100 |
| redis | redis:7.4.2 | 512 MiB | 数据上限 256 MiB，noeviction，AOF |
| rabbitmq | rabbitmq:4.0.7-management | 768 MiB | 内存水位 384 MiB，磁盘告警阈值 1 GiB |
| es | elasticsearch:8.18.8 | 2 GiB | 堆 1 GiB，单节点、启用认证 |
| logstash | tikcookie-logstash:8.18.8-jdbc8.4.0 | 1.5 GiB | 堆 512 MiB，无宿主机端口 |

```bash
dc pull mysql redis rabbitmq es
dc build logstash
dc up -d --wait mysql redis rabbitmq es
dc ps
```

`--wait` 应等待四个服务健康后返回成功。失败时停止后续步骤，用 `dc logs --tail=100 <服务名>` 排查，分享日志前脱敏。此时不启动 Logstash。

```bash
dc exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --no-auth-warning ping'
dc exec -T rabbitmq rabbitmq-diagnostics -q ping
curl --fail --user elastic 'http://127.0.0.1:9200/_cluster/health?wait_for_status=yellow&timeout=60s'
```

curl 提示时输入 `.env` 的 ES 密码，不直接写进命令。成功标志：Redis PONG、MQ 节点可达，ES 请求成功且 timed_out=false。

## 5. 核对空卷自动初始化

MySQL 挂载 `deploy/compose/mysql-init.sh` 和 `dss-init.sql`。**仅空数据卷首次启动**，官方入口执行脚本：先检查确认值及两项 MySQL 用户密码格式，再创建六表、`dss_app` 应用账号和 `dss_logstash` 只读账号。不要再次手工导入或重复 CREATE USER。

```bash
dc exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --database=dss --execute="SELECT DATABASE(); SHOW TABLES;"'
dc exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --execute="SHOW GRANTS FOR '\''dss_app'\''@'\''%'\''; SHOW GRANTS FOR '\''dss_logstash'\''@'\''%'\'';"'
```

成功标志：库名为 dss；表为 `users`、`shops`、`products`、`orders`、`favorites`、`reviews`；应用账号只有 dss 库增删改查权限，Logstash 只对 products/shops 有 SELECT。初始无业务数据。

已有数据卷不触发初始化，改 `.env` 不会重置其密码。首次初始化失败或只完成部分步骤时，停止并人工核查，不能自动删除卷重试。此方案不是已有 ECS 的升级或数据迁移流程。

## 6. 再启动 Logstash

```bash
dc up -d --wait logstash
dc ps logstash
dc logs --tail=100 logstash
curl --fail --user elastic 'http://127.0.0.1:9200/dss_shop,dss_product/_mapping?pretty'
```

成功标志：健康检查通过，JDBC 无连接/权限错误，两个索引存在且映射正确；空库文档数可为 0。业务录入后还需验证增量和店铺关联更新。物理 DELETE 不自动清理 ES；Java 重建开关保持 false。

Compose 自动创建普通命名卷 `mysql-data`、`redis-data`、`rabbitmq-data`、`elasticsearch-data`、`logstash-data`，不是 external 卷。默认项目名为 tikcookie-local，实际卷带项目前缀；不要随意改项目名而误建空环境。MySQL/Redis/MQ/ES 的数据和 Logstash 游标等运行状态留在对应卷；卷不是备份。

## 7. 构建前后端

```bash
cd "$API"
mvn -B --no-transfer-progress clean verify
test -s target/dss-0.0.1-SNAPSHOT.jar
sha256sum target/dss-0.0.1-SNAPSHOT.jar
cd "$WEB"
npm ci
npm run build
test -s dist/index.html
```

成功标志：Maven BUILD SUCCESS 且有可执行 JAR，前端构建退出码为 0 且有 dist/index.html。记录跳过项和告警；不要为打包开启真实依赖测试。构建日期和已执行结果见 [TESTING.md](TESTING.md)。

## 8. 用 systemd 启动后端 JAR

仅运行一个后端。以下 unit 使用当前非 root 部署账号，读取同一 `.env`，工作目录固定为 API 仓库。该账号须能读取 `.env` 和 JAR。

```bash
cd "$API"
DEPLOY_USER="$(id -un)"
test "$DEPLOY_USER" != root
sudo tee /etc/systemd/system/tikcookie-api.service > /dev/null <<EOF
[Unit]
Description=TikCookie API
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=simple
User=$DEPLOY_USER
WorkingDirectory=/opt/tikcookie/tikcookie-api
EnvironmentFile=/opt/tikcookie/tikcookie-api/.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -Dserver.address=127.0.0.1 -jar /opt/tikcookie/tikcookie-api/target/dss-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF
sudo systemctl daemon-reload
sudo systemctl enable --now tikcookie-api
sudo systemctl status tikcookie-api --no-pager
curl --fail http://127.0.0.1:8080/api/v1/categories
```

成功标志：服务 active，分类接口 HTTP 200、code=0。排错用 `sudo journalctl -u tikcookie-api -n 100 --no-pager`，分享前脱敏。没有配置 Actuator，不用 `/actuator/health` 作为检查端点。

首次基础检查后，如需抢购演示，再编辑 `.env` 启用并重启。每次启用启动会追加新轮商品，不是原轮无损续跑；不要启用旧 reset timer 或破坏性脚本。

## 9. 安装前端 dist 和 Nginx 配置

这一步会更新专用演示站点。已有站点先备份，确认不会覆盖其他业务。

```bash
sudo cp -a /etc/nginx "/etc/nginx.backup-$(date +%Y%m%d%H%M%S)"
sudo install -d -m 755 /var/www/tikcookie-web/dist
sudo cp -a "$WEB/dist/." /var/www/tikcookie-web/dist/
sudo chmod -R a+rX /var/www/tikcookie-web/dist
sudo install -m 644 "$API/deploy/nginx/tikcookie.conf" /etc/nginx/conf.d/tikcookie.conf
# 专用新主机若仍启用了发行版默认站点，解除这个符号链接，避免命中默认页。
if [ -L /etc/nginx/sites-enabled/default ]; then
  sudo unlink /etc/nginx/sites-enabled/default
fi
sudo nginx -t
```

只有 `nginx -t` 成功后才继续：

```bash
sudo systemctl enable --now nginx
sudo systemctl reload nginx
curl --fail http://127.0.0.1/
curl --fail http://127.0.0.1/api/v1/categories
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/api/v1/internal/shops
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1/swagger-ui.html
```

成功标志：首页返回前端 HTML，API 返回 code=0，内部接口和 Swagger 经 Nginx 返回 404。`/api/` 转发须保留原 `/api/v1` 路径；SPA 路由刷新应返回 index.html。

本机地址为 `http://127.0.0.1/`；可信演示网络使用 `http://<主机IP>/`。对公网开放前增加域名和 HTTPS，核对 `.env` 的图片前缀为浏览器可访问地址。后端和中间件端口默认只在回环监听；管理入口走 SSH 隧道等受控通道。

## 10. 准备演示数据

空库且 `DSS_FLASH_ENABLED=false` 时首页没有商品，这是初始状态。先按 [OSS 资源准备](CONFIGURATION.md#oss-资源准备) 配好图片存储，再执行以下录入。**内部接口直连回环后端，不经过会拦截它们的 Nginx。** 每步确认响应 code=0 后继续；响应不明时先核对，不盲目重复创建。

### 普通店铺和 NORMAL 商品

准备一张小于 5 MB 的真实 jpg/png/webp。`KEY` 的值就是 `.env` 中的 `DSS_INTERNAL_KEY`，不是 OSS 密钥。下面静默输入，避免把真实口令写进命令历史；不要开启 shell 跟踪。

```bash
BASE=http://127.0.0.1:8080/api/v1
read -r -s -p '输入 DSS_INTERNAL_KEY 的值: ' KEY; printf '\n'
read -r -p '真实小图的绝对路径: ' IMAGE
test -r "$IMAGE"
curl --fail-with-body -X POST "$BASE/internal/files/images" \
  -H "X-Internal-Key: $KEY" -F "file=@$IMAGE"
# 复制成功响应中的 data.fileId，不用示例值替代。
read -r -p '上传返回的 data.fileId: ' FILE_ID
curl --fail "$BASE/images/$FILE_ID" -o /dev/null

curl --fail-with-body -X POST "$BASE/internal/shops" \
  -H "X-Internal-Key: $KEY" -H 'Content-Type: application/json' \
  --data-binary @- <<JSON
{
  "name": "教学演示店", "address": "教学演示地址", "category": "FOOD",
  "images": ["$FILE_ID"], "businessHours": "10:00-22:00"
}
JSON
# 从刚创建的店铺响应取 ID，不用固定已有店铺 ID。
read -r -p '店铺返回的 data.id: ' SHOP_ID
curl --fail-with-body -X POST "$BASE/internal/products" \
  -H "X-Internal-Key: $KEY" -H 'Content-Type: application/json' \
  --data-binary @- <<JSON
{
  "shopId": "$SHOP_ID", "name": "教学演示普通套餐",
  "contents": [{"title": "套餐", "items": [{"name": "主食", "count": 1}]}],
  "price": 990, "type": "NORMAL", "image": "$FILE_ID",
  "validDays": 30, "useRules": ["仅教学演示", "无需真实付款"]
}
JSON
read -r -p '商品返回的 data.id: ' PRODUCT_ID
curl --fail "$BASE/products/$PRODUCT_ID"
curl --fail "$BASE/products?sort=latest&page=1&size=10"
unset KEY
```

成功标志：上传返回 fileId 和 url，代理读取成功；新店铺及商品 ID 均来自实际响应，NORMAL 商品自动上架，首页商品流包含该商品。浏览器再打开上传返回的 url，确认所选读图方式可用。搜索须等待 Logstash 同步后另行检查。

### 可选抢购演示

启动器使用 `src/main/resources/db/flash-demo-append.sql` 中的三个图片 key，不会自动上传图片。新 Bucket 若没有这些对象，先用资源管理身份在 OSS 控制台进入目标 Bucket → 文件列表 → 上传，在 `group1/M00/00/00/` 路径下按下列文件名上传真实 JPEG（可复用同一张演示图），不要更改业务源码。

| 商品 | 必须存在的 ObjectKey |
| --- | --- |
| 牛肉面 | `group1/M00/00/00/31bf468adafd43ecad929b5d5e6b6451.jpg` |
| 提拉米苏 | `group1/M00/00/00/56b48505c9534fc192202bd3746b34b4.jpg` |
| 电影票 | `group1/M00/00/00/6f8fc80e1d884219a8c677d6f077c1cf.jpg` |

确认图片可读、演示店铺无冲突且旧 timer 已停用后，再启用：

```bash
cd /opt/tikcookie/tikcookie-api
vi .env  # 改为 DSS_FLASH_ENABLED=true，核对 DSS_JOB_ENABLED=true。
sudo systemctl restart tikcookie-api
sleep 60
curl --fail http://127.0.0.1:8080/api/v1/products/flash
```

约 60 秒后开抢，若调度尚未完成可稍后重查。响应应出现本轮商品和可读图片，实际商品 ID 从响应取得；每次启用启动都会追加新轮，不能写死旧商品 ID，也不运行 reset 脚本。

## 11. 本地前端开发

不安装 dist，另开终端运行：

```bash
cd /opt/tikcookie/tikcookie-web
npm ci
npm run dev -- --host 127.0.0.1
```

打开终端显示的地址，默认 `http://127.0.0.1:5173/`。Vite 将 `/api` 代理到 `http://127.0.0.1:8080`，不去掉 `/api` 前缀。仅用于本地开发；生产使用上一节的 dist 和 Nginx。

## 12. 停止、重启和验证

```bash
# 后端；启用抢购时每次启动都会追加新轮。
sudo systemctl stop tikcookie-api
sudo systemctl start tikcookie-api
sudo systemctl restart tikcookie-api
# Nginx 配置变更后先测试，再重载。
sudo nginx -t && sudo systemctl reload nginx
# 停机先停应用和同步，再停依赖；保留卷。
sudo systemctl stop tikcookie-api
dc stop logstash
dc stop mysql redis rabbitmq es
sudo systemctl stop nginx
# 恢复时反向进行，先等四个基础服务健康。
dc up -d --wait mysql redis rabbitmq es
dc ps
dc up -d --wait logstash
sudo systemctl start tikcookie-api
sudo systemctl start nginx
```

不要执行 `down -v`、清库或 reset。镜像版本、持久卷和资源限制以 `middleware.yml` 为准；卷不是备份。更新前保存数据库备份、JAR 校验值、前端 dist 和配置。回退应用不能替代数据迁移或恢复。

最后按 [TESTING.md](TESTING.md) 验证登录、普通/抢购订单、消息、搜索、图片、数据持久化及备份恢复。在 [RELEASES.md](RELEASES.md) 记录实际 SHA、镜像、配置版本和结果，再确认发布完成。
