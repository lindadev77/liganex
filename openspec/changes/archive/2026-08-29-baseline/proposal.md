## Why

项目初期已通过 README 与 `docs/adr/` 明确了架构、技术栈、协议版本、数据模型与安全取舍。这些决策需要可验证、可追溯的单一事实来源，避免后续 change 偏离基线，也便于面试官直接看到"为什么选 A 不选 B"的权衡记录。

## What Changes

新增四个 capability 的源规范（baseline）：`project-architecture`、`mcp-server`、`cross-border-erp-data`、`security`。本变更仅固化已有决策，不实现任何代码。

## Capabilities

### New Capabilities
- `project-architecture`: 四层架构（MCP server / client-studio / skills / hub）、`liganex-` 仓库前缀约定、Java + Spring Boot 技术栈、文档与决策记录（ADR + OpenSpec）作为事实来源
- `mcp-server`: MCP 2026-07-28 无状态核心、强制请求头契约、tool annotations 副作用声明、inputSchema 参数校验、结构化输出（MCP Apps）、server/discover 发现
- `cross-border-erp-data`: 订单/商品(SKU)/库存三大核心域，以及时区、汇率、库存口径、退货冲回、幂等五类已知数据质量风险的处理约束
- `security`: 轻量授权边界（单一 API Key + 工具白名单）、全量审计日志、演示只读副本、明确不做细粒度授权（对齐 ADR-0002）

### Modified Capabilities
（无）

## Impact

- 与 `docs/adr/ADR-0001-project-name.md`（命名决策）、`docs/adr/ADR-0002-auth-scope.md`（授权范围）同源互补：ADR 记录"为什么"，spec 记录"系统必须怎样"
- 后续 `liganex-mcp-scaffold` 等 change 将在此基础上新增细化 requirement，并经由 archive 合入 main specs
