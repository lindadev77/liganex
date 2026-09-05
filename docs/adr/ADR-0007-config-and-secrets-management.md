# ADR-0007: 后端配置与密钥管理（外部化，不入库）

- **状态**：已采纳（2026-08-29）
- **决策人**：lindadev77
- **关联**：ADR-0002（基础设施层轻量授权）、ADR-0003（B 端租户与配额）、ADR-0006（后端部署拓扑）、OpenSpec config「稳定与探索平衡」约定

## 背景

- 仓库是**开源**项目（Apache-2.0，GitHub public）。任何提交进仓库的密钥 / 敏感配置都是**全球可见且无法彻底消除**的——即使删除 commit，仍存在于 fork、clone 与 git history 中。
- 后端涉及大量敏感项：AI 模型 Key（Volcengine 方舟 / Seedream / 即梦等）、数据库口令、第三方 API Key、消息队列凭证等。
- 既要让协作者 **clone 即能本地跑（dev 配置随手可用）**，又要能**切换环境（test / prod）**，同时敏感项**绝不进仓库**。

## 决策

1. **yml-first**：后端配置以 Spring Boot 的 `application.yml` 体系为唯一真相源，禁止配置散落于 `.properties` 或代码内硬编码。
2. **dev-only 提交**：仅提交 dev 环境的配置（`application.yml` + `application-dev.yml`），且其中**不得含任何真实密钥 / 口令**；其余环境（test / prod）通过 profile 预留切换能力，真实值不提交。
3. **预留环境切换**：保留 `spring.profiles.active` 机制；`application-test.yml` / `application-prod.yml` 以 **`.example` 模板**形式提交（仅占位符、无真实值），运行时经启动参数激活对应 profile 并注入真实配置。
4. **敏感项外部化**：所有敏感配置（AI 模型 Key、DB 口令、第三方密钥、MQ 凭证）通过**启动参数（`-D` / `--spring...`）或环境变量**注入，不写入任何被提交的文件。优先级：启动参数 / 环境变量 > 被 gitignore 的本地覆盖文件（`application-*.local.yml`）> 已提交的占位 yml。
5. **开源红线**：凡真实密钥 / 口令 / 凭证，**绝对禁止提交或推送到 GitHub 仓库**。

## 推荐落地结构

| 文件 | 是否提交 | 内容 |
|---|---|---|
| `application.yml` | 是 | 公共配置（server、pg/redis/rocketmq 地址、默认 `spring.profiles.active=dev`），**无密钥** |
| `application-dev.yml` | 是 | dev 专有（本地库 / 本地 redis 等），**无密钥** |
| `application-test.yml.example` | 是 | test 模板，占位符，无真实值 |
| `application-prod.yml.example` | 是 | prod 模板，占位符，无真实值 |
| `application-*.local.yml` | 否（gitignore） | 本地真实覆盖，运行期用，绝不入库 |
| `application-prod.yml` / `application-test.yml` | 否（gitignore） | 运行期真实值，由启动参数 / 环境变量注入 |

**运行时注入示例**：

```bash
java -jar liganex-studio.jar \
  --spring.profiles.active=prod \
  -Dvolcengine.api-key="${VOLCENGINE_API_KEY}" \
  -Dspring.datasource.password="${DB_PASSWORD}"
```

或由容器 / 启动脚本注入同名环境变量，效果等价。

## 理由

- **开源仓库不可回滚**：密钥一旦 push 即不可逆泄露，必须事前杜绝，而非事后补救。
- **yml-first 与 Spring Boot 原生一致**：配合 ADR-0005（Flyway 版本化迁移）、ADR-0006（模块化单体），无额外框架成本。
- **dev 配置提交但无密钥**：协作者 clone 后即可跑（自行填 Key），又无泄露风险，是开源项目最佳实践。
- 与 ADR-0002「演示只读副本」、ADR-0003「客户级 Key」形成纵深：仓库层先杜绝源码 / 配置泄露，运行层再谈授权与配额。
- `.gitignore` 已加兜底（本地覆盖文件与真实 prod/test yml 不入库，模板以 `.example` 提交），作为纪律之外的第二道防线。

## 边界与触发条件

- 若后续引入配置中心（Nacos / Apollo）或 KMS，敏感项改由配置中心 / 密钥管理服务下发，本 ADR 的「不入库」原则不变，仅注入途径升级。
- 若接入 CI/CD，密钥经平台 Secret / 环境变量注入流水线，同样不落库、不进镜像层明文。

## 后果

面试被问「开源项目怎么管配置和密钥」时，可说明：yml 分层 + profile 切换 + 敏感项外部化注入 + `.gitignore` 兜底 + 开源红线——展示安全工程素养，而非把 Key 写死在配置里。
