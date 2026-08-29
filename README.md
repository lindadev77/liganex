# Liganex

> Agent 应用生态的连接层 —— 让模型接得上工具，让开发者接得上平台，让用户接得上服务。
>
> 以**跨境 ERP**（订单 / 商品 / 库存 / 结算）为落地场景，验证 MCP + Skill + 客户端 + 开放平台的端到端可行性。

[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![MCP](https://img.shields.io/badge/MCP-2026--07--28-red.svg)](https://modelcontextprotocol.org)
[![Stack](https://img.shields.io/badge/Backend-Spring%20Boot%204.1-6DB33F.svg)](https://spring.io)
[![Topics](https://img.shields.io/badge/topics-mcp%20%7C%20agent%20%7C%20erp%20%7C%20cross--border-ecommerce-orange.svg)](#)

---

## 这是什么

Liganex 是一套围绕 **Model Context Protocol (MCP)** 构建的开源实验性项目，目标是把"Agent 应用生态"这件事从概念落到一套能跑、能演示、能讲清楚的系统。

它包含四块咬合的组件：

| 组件 | 定位 | 仓库 |
|---|---|---|
| **Liganex MCP Server** | 供给侧：把 ERP 数据与能力暴露为 MCP tools | `liganex-mcp` |
| **Liganex Studio** | 客户端：轻量 MCP Host / 编排运行时（B 端前端 + 后端，同仓） | `server/liganex-studio-backend` + `studio-frontend` |
| **Liganex Skills** | 能力封装：面向跨境 ERP 的领域 Skill 包 | `liganex-skills` |
| **Liganex Hub** | 分发：Skill 注册表 + MCP Server 注册中心 | `liganex-hub` |

业务场景以**跨境 ERP**为锚点：多平台 SKU、多仓库存、跨境头程时效、汇率结算、退货冲回 —— 这些是你真正跑过、别人抄不走的数据模型。

## 为什么不直接叫"跨境 ERP"

名字保持通用。`liganex` 全站零同名（GitHub total: 0），而垂直化的名字（如 `temu-erp-agent`）会把天花板锁死还绑死平台。跨境 ERP 是**场景**，不是**前缀**。

## 架构（四层）

```
┌─────────────────────────────────────────────┐
│                 Liganex Hub                   │
│   Skill Registry + MCP Server Registry + 文档  │
└───────────────┬───────────────────────────────┘
                │ 注册 / 发现
┌───────────────┴───────────────┐
│      Liganex Studio (客户端)     │ ← 轻量 MCP Host
│  编排 · 工具路由 · 上下文管理     │
└───────┬───────────────────┬─────┘
        │ 调用              │ 加载
┌───────▼───────┐    ┌──────────────▼──────────────┐
│  Liganex MCP  │    │        Liganex Skills        │
│    Server     │    │ replenishment · margin · ... │
│ ERP 数据/工具  │    └──────────────────────────────┘
└───────────────┘
```

## 仓库清单

| 仓库 | 内容 | 优先级 |
|---|---|---|
| `liganex` | 本仓库 —— 总入口、架构总览、Roadmap | P0 |
| `liganex-mcp` | Java + Spring Boot 实现的 MCP Server | P0 |
| `liganex-docs` | 设计文档、Spec、ADR | P0 |
| `liganex-studio`（已并入本仓） | 客户端运行时 → `server/liganex-studio-backend/` + `studio-frontend/` | P1 |
| `liganex-skills` | 跨境 ERP 领域 Skill 包（MIT） | P1 |
| `liganex-hub` | 注册表与分发 | P2 |
| `liganex-support` | 智能客服问答示例 | P2 |
| `.github` | 组织级 CI / 模板 | P0 |

> 前后端同仓：Studio 客户端后端位于 `server/liganex-studio-backend/`（Spring Boot 4.1 多模块之一），
> 前端位于 `studio-frontend/`（React 19 + Vite 8 + antd 6），不单独拆前端仓（对齐 Dify / Coze Studio / Langflow）。
>
> 本仓目录结构（后端为 Maven 多模块，前端为独立 Vite 工程）：
> ```
> liganex/
> ├── server/                      # 后端多模块（liganex-server 聚合 POM，Spring Boot 4.1 + Java 21）
> │   ├── liganex-common/          # 公共工具（占位）
> │   ├── liganex-order/           # 订单领域（占位，当前由 studio-backend 承载，ADR-0009 服务化就绪）
> │   ├── liganex-mcp/             # MCP Server（占位）
> │   └── liganex-studio-backend/  # ★ Studio 后端：auth / 订单 / 开放平台 / MCP 鉴权
> ├── studio-frontend/             # ★ Studio 前端：React 19 + Vite 8 + antd 6
> ├── infra/local-dev/             # 本地基础设施（PG + Redis + RocketMQ）
> ├── docs/adr/                    # 架构决策记录
> └── openspec/                    # 变更提案
> ```

## 本地起服（Quick Start）

最小可跑通的全栈链路：**基础设施（PG / Redis）→ Studio 后端 → Studio 前端**。完整环境搭建（含 OrbStack、RocketMQ、踩坑记录）见 [docs/dev-setup.md](docs/dev-setup.md)。

```bash
# 0) 基础设施：PG(5432) + Redis(6379)
cd infra/local-dev && cp .env.example .env && docker compose up -d

# 1) 后端（必须用 JDK 21，系统默认是 JDK 8）
export JAVA_HOME=/Users/Admin/Dev/tools/jdk21/Contents/Home
cd server
mvn -pl liganex-studio-backend -am spring-boot:run \
  -Dliganex.security.jwt.secret="$(openssl rand -base64 48)" \
  -Dliganex.internal.service-api-key="dev-internal-key" \
  -Dliganex.open.app-secret-master-key="$(openssl rand -base64 32)"
#   → 监听 http://127.0.0.1:8081

# 2) 前端（另开终端）
cd studio-frontend
npm install            # 首次或依赖变更；已含 lightningcss 原生二进制（arm64 macOS）
npm run dev           # Vite 反代 /api → http://127.0.0.1:8081，访问 http://127.0.0.1:5173
```

> 改 Java 版本后务必 `mvn clean` 全量重编（增量编译不会重编未改源码，会残留旧字节码导致运行时 `UnsupportedClassVersionError`）。密钥经 `-D` 注入，缺失会 fail-fast 拒启动（ADR-0007：密钥不入库）。

**验证闭环**：打开 `http://127.0.0.1:5173` → 注册 → 登录 → 「我的应用」创建应用（拿到一次性 appSecret）→ 绑定 `order:read` 权限，即可在页面跑通。后端 `GET /actuator/health` 返回 `{"status":"UP"}` 即就绪。

## 技术选型要点

- **后端**：Spring Boot 4.1 + Java 21 LTS（企业 ERP 主栈，MCP Java SDK 可用，生态稀缺）
- **协议**：MCP **2026-07-28** 无状态核心 —— 单端点 POST、移除 `initialize`/`Mcp-Session-Id`、每请求 `_meta` 携带版本能力、`server/discover` 强制发现
- **工具标注**：用 `tool.annotations`（`readOnlyHint` / `destructiveHint` / `idempotentHint` / `openWorldHint`）声明副作用等级，既是安全也是体验优化
- **展示**：MCP Apps（`outputSchema` + `structuredContent`）让库存/利润看板结构化渲染

## 安全取舍（实验项目）

当前版本**不实现** OAuth 2.1 / Token Audience Binding / Step-Up Auth（避免吞噬工作量），但保留工程底线：

- tool annotations 声明副作用等级
- `inputSchema` 参数校验（协议强制）
- 单一 API Key + 工具白名单（约一小时工作量）
- 全量审计日志（一张表）
- 演示用只读 / 影子数据副本

详见 [`docs/adr/ADR-0002-auth-scope.md`](docs/adr/ADR-0002-auth-scope.md)。

## 路线

1. **P0**：主仓 README + `liganex-mcp` 骨架（2026-07-28 请求头处理 + annotations）
2. **P1**：跨境 ERP Skill 包（补货建议 / 利润核算 / 异常订单）
3. **P1**：`liganex-studio` 客户端运行时
4. **P2**：`liganex-hub` 注册表 + 智能客服

## Topics

`mcp` · `model-context-protocol` · `agent` · `erp` · `java` · `spring-boot` · `cross-border-ecommerce`

## 开源许可

本项目基于 [Apache License 2.0](LICENSE) 授权（`SPDX-License-Identifier: Apache-2.0`）。

```
Copyright 2026 Linda

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

允许自由使用、修改与再分发（含商业用途），但须保留版权声明与许可声明；作者不承担任何担保责任。完整条款见 [LICENSE](LICENSE)。
