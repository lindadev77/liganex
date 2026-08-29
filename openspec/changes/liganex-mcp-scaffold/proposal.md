## Why

作为 P0 第一步，需要先有一个可运行、且能被现成 MCP 客户端直接验证的服务器骨架。它把 baseline 中 `mcp-server` 的通用协议约束落地为具体实现，作为后续 skills / studio / hub 的供给侧基础——没有它能调用的服务端，其他模块都无从验证。

## What Changes

在 `liganex-mcp` 模块中实现：Maven/Spring Boot 骨架、无状态 POST 端点与头契约、首个示例工具 `order-lookup`（带 annotations 与 inputSchema）、`server/discover` 发现、API Key 鉴权中间件、审计日志服务。

## Capabilities

### New Capabilities
（无新增 capability，均在已有 `mcp-server` 下细化）

### Modified Capabilities
- `mcp-server`: 新增骨架实现类 requirement（端点、示例工具、鉴权、审计），将 baseline 的协议约束落到代码层

## Impact

- 依赖：Spring Boot 3.x（LTS 内最新稳定版，与主项目一致）、Java 21+（最低 21，优先 LTS 内最新稳定版）、MCP Java SDK（若官方 SDK 已覆盖 2026-07-28 无状态核心）
- 受影响代码：`liganex-mcp` 模块
- 下游受益：`liganex-skills` / `liganex-studio` 将获得可直连验证的服务端
