# TikCookie API 贡献指南

感谢参与 TikCookie 后端开发。本项目采用 `main + 短期任务分支 + Pull Request` 的协作模式。

## 开始任务前

1. 确认任务目标、验收标准和负责人。
2. 确认是否涉及接口契约、数据结构、中间件或配置项。
3. 同步最新主分支：

   ```bash
   git switch main
   git pull --ff-only origin main
   ```

4. 创建任务分支：

   ```bash
   git switch -c feat/<short-description>
   ```

## 分支命名

| 类型 | 用途 | 示例 |
| --- | --- | --- |
| `feat/` | 新功能 | `feat/order-create` |
| `fix/` | 缺陷修复 | `fix/search-index-sync` |
| `refactor/` | 不改变行为的重构 | `refactor/product-service` |
| `test/` | 测试相关 | `test/order-controller` |
| `docs/` | 文档变更 | `docs/openapi-rules` |
| `chore/` | 工程与依赖维护 | `chore/update-maven-wrapper` |

分支名使用小写英文和连字符，不使用个人姓名或长期存在的个人分支。

## 提交规范

提交信息采用 Conventional Commits 风格：

```text
<type>(<scope>): <summary>
```

示例：

```text
feat(order): create order endpoint
fix(search): retry failed index message
docs(config): add OSS variable descriptions
```

常用 `type`：`feat`、`fix`、`refactor`、`test`、`docs`、`chore`、`build`、`ci`。

## 本地检查

后端项目骨架建立后，提交前至少执行仓库声明的：

- 编译
- 单元测试
- 集成测试（涉及数据库或中间件时）
- 应用打包
- 静态检查或格式检查（如项目启用）

不得通过删除测试、跳过检查或关闭安全校验来规避失败。

## Pull Request 要求

PR 必须：

- 使用清晰标题，说明修改目的。
- 关联任务或 Issue。
- 描述主要修改、验证结果和影响范围。
- 说明接口、数据、配置、中间件和发布顺序变化。
- 对数据变更说明兼容与恢复办法。
- 保持范围单一，避免混入无关格式化或重构。

评审意见处理完毕、CI 通过且获得批准后，由代码管理员 Squash 合并。

## 禁止事项

- 直接推送或强制推送 `main`。
- 提交 `.env`、密码、Token、AccessKey、私钥或生产配置。
- 未经说明破坏接口或数据兼容性。
- 将 Elasticsearch 作为不可恢复的唯一业务数据来源。
- 在一个 PR 中混合多个不相关需求。

