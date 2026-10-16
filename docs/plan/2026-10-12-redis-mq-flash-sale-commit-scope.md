# 秒杀改造提交范围核对（历史计划）

> 本文记录 2026-10-13 当时的提交范围，不是当前提交操作指令。历史路径和测试结果只适用于原记录，当前功能与验证见[需求](../需求文档.md)和[测试说明](../TESTING.md)。

> 2026-10-13；当前两个仓库均在 `feat/redis-mq-flash-sale`，暂存区为空。未获得本轮提交确认，不执行 `git add` / `git commit` / 推送 / 合并。

## 必须排除的原有工作

后端以下路径在开始改造前已存在修改或未跟踪内容，禁止整仓库 `git add .` 将其带入本次提交：

- `D:/codespace/TikCookie/tikcookie-api/src/main/resources/imgs/img1.jpg` 的既有删除。
- `D:/codespace/TikCookie/tikcookie-api/src/test/java/com/dss/file/service/impl/OssFileStorageLiveTest.java` 的既有修改。
- `D:/codespace/TikCookie/tikcookie-api/src/main/resources/images/`。
- `D:/codespace/TikCookie/tikcookie-api/src/main/resources/oss-url.md`。该清单只被读取，用其既有公开 ObjectKey，不将其作为本次新增文件提交。
- `D:/codespace/TikCookie/tikcookie-api/docs/superpowers/plans/2026-10-09-flash-demo-cycle.md`。
- 原有 `D:/codespace/TikCookie/tikcookie-api/deploy/` 内容，除下面明确列出的本次隔离验收文件。

不提交任何 `.env`、`target/`、`logs/`、前端 `.npm-cache/`、`node_modules/`、`dist/` 或 Python `__pycache__`。没有改动 `dss-init.sql`、后端 POM 或前端 package/lock 文件。

## 后端候选范围

仓库：`D:/codespace/TikCookie/tikcookie-api`。

- `.gitignore`、`.env.example`。
- `src/main/java/com/dss/flash/`、`src/main/java/com/dss/order/support/`。
- OrderController、OrderService/OrderServiceImpl、OrderMapper、Order实体、两个订单任务。
- ProductServiceImpl、ProductErrorCode、FlashProductVO、ProductVO。
- DssProperties、RabbitCommonConfig、SearchServiceImpl。
- `src/main/resources/application.yml`、`src/main/resources/db/flash-demo-append.sql`、`src/main/resources/lua/flash-reserve.lua`、`flash-confirm.lua`、`flash-release.lua`。
- 新增 `src/test/java/com/dss/flash/`、`order/`、`product/` 测试；修改的 LogstashApplicationStartupTest 与 SearchServiceImplTest。
- **仅以下 deploy 文件**：`deploy/integration/compose.yml`、`run_flash_it.py`、`run_native_flash_it.py`、`deploy/tests/test_flash_lua.py`。
- 本次 `docs/FLASH_SALE.md` 及 API_CONTRACT / CONFIGURATION / DEPLOYMENT / PROGRESS / PROJECT_GUIDE / 需求文档 / 接口文档 / 中间件配置的更新。

## 前端候选范围

仓库：`D:/codespace/TikCookie/tikcookie-web`，开始时工作树干净。

- `.gitignore`。
- App、Home、Detail、Login、OrderPay、Success、gerenzhuzhi；新增 FlashQueue。
- `src/api/flash.js`、`order.js`、`user.js`；router。
- request、identity、flash-client、flash-flow、payment-flow、server-time helpers。
- 本次 `tests/` 测试及加载真实页面/Axios的测试工具，不提交测试缓存。

## 仓库外的实施记录

`D:/codespace/TikCookie` 本身不是 Git 仓库。原规划更新、落地记录和本文位于其 `docs/plan/` 下，不会通过两个子仓库的提交自动保存；未经确认不将它们搬入另一个仓库，也不初始化新的根仓库。

## 提交前最后检查

确认提交后按明确路径暂存，再核对 staged diff：

1. 暂存区无上述原有工作、真实环境配置、缓存/构建产物。
2. `git diff --cached --check` 通过。
3. staged schema/POM/package diff仍为空。
4. 以最终工作树验证后端test/verify、18Lua、76Node/build与完整隔离IT；详细证据在实施记录中。
5. 提交需如实描述当前逻辑分组；不能把今天整理的提交冒充此前已逐阶段独立提交的历史。用户确认前保留现状，不改写历史。
