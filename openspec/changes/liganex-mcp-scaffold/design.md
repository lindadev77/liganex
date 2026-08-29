## Context

baseline 已锁定 `mcp-server` 的协议约束（2026-07-28 无状态核心、头契约、tool annotations、inputSchema、MCP Apps 结构化输出、server/discover）。本变更将其落地为最小可运行骨架，优先保证"能被现成客户端直接验证"，而非功能完整。

## Goals / Non-Goals

**Goals:**
- 跑通端到端的最小 MCP 服务器：请求 → 头校验 → 方法分发 → 示例工具 → 结构化返回 → 审计落库

**Non-Goals:**
- 不在此骨架中接入真实 ERP 数据库（留给后续 change）
- 不实现 OAuth 2.1 等细粒度授权（对齐 `security` capability）
- 不实现多个业务工具，仅 `order-lookup` 作为模板

## Decisions

- 用 Spring Boot 而非裸 HTTP server：复用主项目技术栈，降低企业场景的认知门槛，也与 TS/Python 主流实现形成差异化
- 示例工具选 `order-lookup`：一个工具即可覆盖 `readOnly` + `inputSchema` + 结构化输出三个关键点，且天然贴合跨境 ERP 场景
- 鉴权采用单一 API Key + 白名单（对齐 `security` capability），不为骨架引入 OAuth

## Risks / Trade-offs

- MCP Java SDK 成熟度：若官方 SDK 尚未完整覆盖 2026-07-28 无状态核心，需手写请求头解析与 JSON-RPC 信封；实现前先确认 SDK 能力
- 骨架仅含一个工具，演示价值有限；但它是后续 tools 的模板，优先级高于数量

## Status (2026-08-29)

实现延期：当前仍处于规划态，不写代码。前置依赖为 ADR-0003（B 端配额边界，已于 2026-08-29 采纳）与 local-dev-env change（本地基础设施选型已登记）。待基础设施选型与 B 端配额边界在代码中可落地后，再启动本 change 的 10 项任务。
