# 技术设计：注册登录 / 订单查询 / 开放平台 / MCP + skill 端到端闭环

## Context

当前项目状态：

- 仓库已按职责拆分：`liganex-studio`（frontend + backend）、`liganex-mcp`、`liganex-hub`（开放平台）、`liganex-gen`、`liganex-skills`
- 本地基础设施已就绪并可验证：Postgres 18.6 + pgvector 0.8.6、Redis 8.10.1、RocketMQ 5.3.3（`infra/local-dev/docker-compose.yml`）
- 已定架构决策：模块化单体无网关（ADR-0006）、Flyway 版本化迁移（ADR-0005）、密钥外部化（ADR-0007）、前端 React 19 + antd 6（ADR-0008）、订单归属与内部服务接口（ADR-0009）
- 后端栈：Java 21+ / Spring Boot 3.x；MCP 协议为 2026-07-28 stateless core（单端点 POST、无握手、必带 `MCP-Protocol-Version` / `Mcp-Method` / `Mcp-Name` 头）

本次要打通：**人（B 端用户）** 与 **应用（MCP 客户端）** 两条独立链路，共享同一份订单领域能力。

## Goals / Non-Goals

**Goals**

- 用户可注册登录，在 B 端页面查询订单
- 用户可在开放平台创建应用、绑定接口权限集，获得 `appid` / `appsecret`
- 客户端安装 liganex skill 后，用对话查询订单，全程经 MCP 协议并受权限集约束
- MCP 侧具备防重放、配额、审计能力
- 订单查询为内部服务接口形态，为未来拆分预留（ADR-0009）

**Non-Goals**：见 proposal 的 Non-goals（不做 OAuth、不做订单写入、不引网关、不拆独立订单服务）

## Decisions

### 1. 订单领域归属：模块内独立，接口对外（ADR-0009）

订单作为 `studio/backend` 内的独立领域模块，对外暴露两套接口：

| 接口 | 消费方 | 鉴权 |
|---|---|---|
| `/api/v1/orders/*` | B 端前端 | 用户 JWT |
| `/internal/v1/orders/*` | `liganex-mcp` | 内部服务凭证（服务间 API Key） |

模块约束：独立包结构、独立 DTO、不外泄持久化实体；定义 `OrderQueryClient` 接口（本地 / 远程两种实现，配置切换），使未来拆服务时消费方零改动。**严禁 MCP 直连订单库。**

### 2. 两套鉴权体系（关键设计）

| 体系 | 面向 | 凭证 | 校验位置 |
|---|---|---|---|
| 用户会话 | 人（B 端前端） | JWT（access + refresh） | `studio/backend` Spring Security filter |
| 应用凭证 | 应用（MCP 客户端） | `appid` + HMAC 签名 | `liganex-mcp` 入站 filter |
| 内部服务 | 服务（`mcp` → `studio`） | 服务间 API Key（Header） | `studio/backend` internal filter |

**为什么应用侧用签名而非明文传 secret**：MCP over HTTP 场景下，明文 Bearer secret 一旦在日志、代理或客户端配置中泄露即可被完整伪造；HMAC 签名不传输 secret 本身，且天然带防重放能力。

签名方案：

```
X-Liganex-App-Id:  <appid>
X-Liganex-Timestamp: <毫秒时间戳>
X-Liganex-Nonce:    <随机串>
X-Liganex-Signature: Base64(HMAC-SHA256(appsecret,
      appid + "\n" + timestamp + "\n" + nonce + "\n" + method + "\n" + path + "\n" + SHA256(body)))
```

服务端校验顺序：① 时间戳在 ±5 分钟窗口内 → ② nonce 在 Redis 中 `SET NX EX`（防重放）→ ③ 取 `appsecret_hash` 比对签名 → ④ 应用状态正常 → ⑤ 拥有该 tool 所需 scope → ⑥ 配额未超限。任一步失败即拒绝并记录审计日志。

### 3. 权限集（scope）模型

- 权限码格式 `{resource}:{action}`，如 `order:read`、`order:write`、`product:read`
- 权限字典以种子数据维护（Flyway `R__` 或 `V__` 初始化），应用与权限为多对多绑定
- 每个 MCP tool 在注册元数据中声明所需 scope（与 MCP 2026-07-28 的 tool annotations 并列，作为 Liganex 自有扩展字段）
- 校验发生在 tool 调用前；未授权返回结构化错误，不泄露权限集全貌

### 4. 校验性能与配额：Redis 8

- `appid → {secret_hash, scopes, status}` 缓存，TTL 60s，权限变更时主动失效
- nonce 防重放键 TTL = 时间窗（10 分钟）
- 配额计数用 Redis 原子自增（按应用 + 周期），软限额告警、硬限额拒绝（ADR-0003）

### 5. 数据模型（Flyway 迁移，ADR-0005）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `user` | id, email/username, password_hash, status, created_at | 密码 BCrypt，不存明文 |
| `open_app` | id, app_id, app_secret_hash, name, owner_user_id, status | secret 仅哈希入库 |
| `permission` | code, name, description | 权限字典（种子数据） |
| `app_permission` | app_id, permission_code | 应用与权限集绑定 |
| `app_call_log` | id, app_id, tool, permission, ts, result, latency | 全量审计（ADR-0002） |
| `quota_usage` | app_id, period, used | 配额计数 |
| `order` / `order_item` | 业务字段 + 地区 + 创建时间 | 按 ADR-0004 分区（时间 Range + 地区 List） |

建表后随迁移提供种子数据：权限字典、演示订单（覆盖多地区 / 多状态），便于本地联调与集成测试（ADR-0005 种子数据约定）。

### 6. 三条调用链路

1. **B 端用户链路**：浏览器 →（nginx `/api` 反代）→ `studio/backend` `/api/v1/auth/*`、`/api/v1/orders/*`（JWT）
2. **开放平台链路**：浏览器 → `studio/backend` →（内部调用）→ `liganex-hub`：应用 CRUD、权限集绑定、secret 重置
3. **MCP / skill 链路**：客户端 + skill →（MCP + 签名）→ `liganex-mcp` →① Redis 校验缓存 / hub 查应用与权限 →②（内部服务凭证）→ `studio/backend` `/internal/v1/orders/*` → 订单分区表

### 7. 前端页面与接口（React 19 + antd 6，ADR-0008）

页面：登录/注册页、订单查询页（分页 + 地区/状态/时间筛选）、开放平台应用管理页（创建应用、查看 appid、绑定权限集、重置 secret）。

所有前端请求经 nginx `/api` 前缀反代（local-dev-env 约定），不开启宽松 CORS。

### 8. skill 形态

`liganex-skills` 仓库新增 `order-query` skill：

- `SKILL.md`：触发场景（询问订单/销量/履约）、前置条件（需配置 MCP endpoint + appid + appsecret）、工具清单、对话示例
- 客户端安装后，用户自然语言提问（如"最近一周美国站待发货订单有多少"）→ 客户端解析意图 → 调 MCP tool `query_orders`（携带签名头）→ 结果以对话形式呈现
- 附签名示例代码（shell / Python / TS），降低接入成本

## Risks / Trade-offs

- **签名增加客户端实现成本**：skill 需实现 HMAC 签名 → 提供示例代码与 SDK 片段；也可在 dev 环境提供"简化模式"（明文 appid + secret，仅本地）
- **MCP 依赖 `studio/backend` 内部接口**：必须配置超时（≤2s）、重试与降级；订单查询失败返回明确错误，**不得拖垮 MCP server 或其他 tool**（ADR-0009）
- **appsecret 只展示一次**：用户遗失需走重置接口（旧 secret 立即失效）
- **nonce 存 Redis 增加一次 IO**：校验阶段已需读缓存，可接受；高频应用可本地短窗 + Redis 兜底
- **配额先做简单计数**：后续可接入 Redis 8 更丰富的能力，不阻塞本次闭环
- **两套鉴权并存易混淆**：通过路径前缀（`/api` vs `/internal`）与校验器命名严格区分，并在文档中标注

## Open Questions

- 是否需要 refresh token 轮换（rotation）与登出黑名单：一期采用 refresh token + 短期 access token，黑名单视需要再引入
- `liganex-hub` 是否在本期独立部署：一期应用管理能力可随 `studio/backend` 一同部署，接口契约按独立服务设计
