## Why

Liganex 需要一条**完整可演示的端到端闭环**，把两层产品面第一次真正打通：

用户在 B 端注册登录 → 在开放平台创建应用、绑定接口权限集 → 拿到 `appid` / `appsecret` → 在类 WorkBuddy 的客户端安装 liganex skill → **通过对话查询跨境 ERP 订单**。

这条链路的价值在于它同时覆盖了**两套彼此独立的鉴权体系**：面向人的用户会话（B 端前端）与面向应用的凭证（MCP / skill）。它是本项目区别于普通 CRUD 项目的核心叙事，也是 `liganex-mcp-scaffold` 首个真实业务落地（该 change 此前因依赖未就绪而标记实现延期）。

## What Changes

新增五个 capability，形成一个从「账号」到「应用授权」到「对话查询业务数据」的完整闭环：

- `user-auth`：注册、登录、用户会话（JWT）
- `order-query`：跨境 ERP 订单查询，同时提供前端页面接口与内部服务接口
- `open-platform-app`：开放平台应用管理、`appid`/`appsecret` 签发、接口权限集绑定
- `mcp-access-control`：MCP server 侧的应用凭证校验、权限集校验、防重放、配额与审计
- `liganex-skill`：供客户端安装的 skill 定义，支持以对话方式查询订单

## Non-goals

- **不做 OAuth 2.1 / 三方授权**（遵循 ADR-0002，应用级凭证采用 appid + HMAC 签名）
- **不做订单写入、履约、支付**：本闭环只做订单查询
- **不引入 API 网关**（遵循 ADR-0006）
- **不将订单拆为独立部署服务**（见 ADR-0009，按独立领域模块 + 内部服务接口实现）
- **不包含 AI 生成能力**（属 `bend-ai-generation` 独立 change）
- **不实现多租户隔离**（当前为单租户形态，ADR-0003 的租户边界在应用层）

## Capabilities

### New Capabilities
- `user-auth`: 用户注册、登录与会话管理（凭证哈希存储、JWT 签发与刷新、退出）
- `order-query`: 跨境 ERP 订单查询，含前端页面接口与内部服务接口两套入口
- `open-platform-app`: 开放平台应用、`appid`/`appsecret` 与接口权限集（scope）管理
- `mcp-access-control`: MCP server 对应用凭证、权限集、配额的校验与调用审计
- `liganex-skill`: 客户端可安装的 skill，通过 MCP 对话查询订单

### Modified Capabilities
（无，本次均为首次新增）

## Impact

- **ADR-0002 / ADR-0003**：应用级凭证与权限集是「客户级 API Key + 配额」的具体落地，沿用其轻量授权思路，不引入 OAuth
- **ADR-0004**：订单表按时间 Range + 地区 List 分区；校验缓存与配额计数落在 Redis 8
- **ADR-0006**：不引入网关，服务间直连并以内部服务凭证鉴权
- **ADR-0007**：`appsecret` 仅哈希入库、明文只在创建时返回一次；内部服务凭证经启动参数/环境变量注入，绝不入库
- **ADR-0008**：前端以 React 19 + antd 6 实现登录页、订单查询页与开放平台应用管理页
- **ADR-0009**：订单领域归属与内部服务接口契约由本 change 落地
- **`liganex-mcp-scaffold`**：本闭环解除其实现延期，MCP 骨架在此获得首个真实 tool（`query_orders`）
