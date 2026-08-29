## 1. 数据模型与迁移（Flyway，ADR-0005）
- [x] 1.1 编写 V1__user_and_auth.sql：`app_user` 表（email / password_hash / status）、唯一索引
- [x] 1.2 编写 V2__open_platform.sql：`open_app`、`permission`、`app_permission`、`app_call_log`、`quota_usage`
- [x] 1.3 编写 V3__order.sql：`customer_order` 分区表（月 RANGE + 地区 LIST 子分区，ADR-0004）
- [x] 1.4 编写种子数据：权限字典（`order:read` 等）+ 演示订单（12 条，多地区 / 多状态）
- [ ] 1.5 用 Testcontainers 跑一遍迁移，确保测试库与生产库同源

## 2. 用户认证（user-auth）
- [x] 2.1 实现注册接口：参数校验、邮箱唯一性、BCrypt 哈希、默认状态
- [x] 2.2 实现登录接口：校验凭证、签发 access / refresh token（JWT，HS256）
- [x] 2.3 实现 JWT 校验 filter 与 refresh 接口，接入 Spring Security
- [ ] 2.4 补集成测试：注册重复、登录失败、token 过期与刷新

## 3. 订单领域模块与内部服务接口（ADR-0009）
- [x] 3.1 建 order 模块包结构、DTO 与 Mapper，不外泄持久化实体
- [x] 3.2 实现订单查询领域服务（分页、地区 / 状态 / 时间筛选）
- [x] 3.3 实现 `/api/v1/orders/*` 前端接口（JWT 鉴权）
- [ ] 3.4 实现 `/internal/v1/orders/*` 内部接口（服务间 API Key 鉴权）
- [x] 3.5 定义 `OrderQueryClient` 接口 + 本地实现（远程实现待拆服务时补）

## 4. 开放平台应用与权限集（open-platform-app）
- [ ] 4.1 实现应用创建接口：生成 appid / appsecret，secret 仅哈希入库、明文只返回一次
- [ ] 4.2 实现应用列表 / 详情 / 停用接口（校验归属用户）
- [ ] 4.3 实现权限集查询与绑定 / 解绑接口
- [ ] 4.4 实现 appsecret 重置接口（旧 secret 立即失效）
- [ ] 4.5 权限变更时主动失效 Redis 中的应用缓存

## 5. MCP 侧鉴权与权限校验（mcp-access-control）
- [ ] 5.1 实现 HMAC 签名校验 filter：时间窗、nonce 防重放、签名比对
- [ ] 5.2 实现应用状态与 scope 校验，tool 元数据声明所需权限
- [ ] 5.3 接入 Redis 缓存（appid → secret_hash / scopes / status，TTL 60s）
- [ ] 5.4 实现配额计数（Redis 原子自增）与软硬限额拦截（ADR-0003）
- [ ] 5.5 实现调用审计日志落库（app_call_log，ADR-0002）

## 6. MCP tool 与内部调用
- [ ] 6.1 实现 `query_orders` tool（声明 `order:read` scope）与结构化出参
- [ ] 6.2 实现 MCP → studio 内部订单接口调用（内部凭证、超时 ≤2s、重试与降级）
- [ ] 6.3 MCP 骨架落地：2026-07-28 无状态协议头与 `server/discover`（解除 liganex-mcp-scaffold 延期）

## 7. skill 开发（liganex-skill）
- [ ] 7.1 编写 `order-query` skill 的 SKILL.md：场景、前置配置、工具清单、对话示例
- [ ] 7.2 提供签名示例代码（shell / Python / TS）与本地联调说明

## 8. 前端页面（React 19 + antd 6，ADR-0008）
- [ ] 8.1 搭建 Vite + React 19 + TS 骨架与 antd 6 主题（cssVar）
- [ ] 8.2 实现登录 / 注册页与 token 存储、请求拦截器
- [ ] 8.3 实现订单查询页（分页 + 筛选，ProTable）
- [ ] 8.4 实现开放平台应用管理页（创建、查看 appid、绑定权限集、重置 secret）
- [ ] 8.5 配置 nginx `/api` 反代，验证无跨域问题

## 9. 端到端联调与验证
- [ ] 9.1 跑通「注册登录 → 创建应用 → 绑定 order:read → 对话查订单」全链路
- [ ] 9.2 验证负向用例：错误签名、过期时间戳、重放 nonce、越权 scope、超配额
- [ ] 9.3 验证内部接口降级：订单服务不可用时 MCP 返回明确错误而不崩溃
- [ ] 9.4 运行 `openspec validate` 并归档本 change
