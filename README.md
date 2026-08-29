# Liganex

> Agent 应用生态的连接层 —— 让模型接得上工具，让开发者接得上平台，让用户接得上服务。
>
> 以**跨境 ERP**（订单 / 商品 / 库存 / 结算）为落地场景，验证 MCP + Skill + 客户端 + 开放平台的端到端可行性。

[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![MCP](https://img.shields.io/badge/MCP-2026--07--28-red.svg)](https://modelcontextprotocol.org)
[![Stack](https://img.shields.io/badge/Backend-Spring%20Boot%203.x-6DB33F.svg)](https://spring.io)
[![Topics](https://img.shields.io/badge/topics-mcp%20%7C%20agent%20%7C%20erp%20%7C%20cross--border-ecommerce-orange.svg)](#)

---

## 这是什么

Liganex 是一套围绕 **Model Context Protocol (MCP)** 构建的开源实验性项目，目标是把"Agent 应用生态"这件事从概念落到一套能跑、能演示、能讲清楚的系统。

它包含四块咬合的组件：

| 组件 | 定位 | 仓库 |
|---|---|---|
| **Liganex MCP Server** | 供给侧：把 ERP 数据与能力暴露为 MCP tools | `liganex-mcp` |
| **Liganex Studio** | 客户端：轻量 MCP Host / 编排运行时 | `liganex-studio` |
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
| `liganex-studio` | 客户端运行时 | P1 |
| `liganex-skills` | 跨境 ERP 领域 Skill 包（MIT） | P1 |
| `liganex-hub` | 注册表与分发 | P2 |
| `liganex-support` | 智能客服问答示例 | P2 |
| `.github` | 组织级 CI / 模板 | P0 |

> 前后端同仓：业务仓库内用 `backend/` + `frontend/` 目录，不单独拆前端仓（对齐 Dify / Coze Studio / Langflow）。

## 技术选型要点

- **后端**：Spring Boot 3.x + Java（企业 ERP 主栈，MCP Java SDK 可用，生态稀缺）
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
