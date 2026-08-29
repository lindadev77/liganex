## Purpose

MCP server（`liganex-mcp`）的协议约束与实现规范。本 capability 锁定项目所遵循的 Model Context Protocol 版本与服务器侧契约，确保实现与 2026-07-28 最新规范一致，形成对存量过时实现的差异化。

## ADDED Requirements

### Requirement: 遵循 MCP 2026-07-28 无状态核心
The MCP server SHALL implement the 2026-07-28 stateless protocol core: no `initialize` handshake, no `Mcp-Session-Id`, and a single POST endpoint (GET/DELETE return 405).

#### Scenario: 无状态请求
- **GIVEN** 一个 MCP 请求
- **WHEN** 客户端发送 POST
- **THEN** 每个请求在 `_meta` 中携带协议版本与能力声明，服务端不维持任何会话状态

### Requirement: 强制请求头契约
The MCP server SHALL require the headers `MCP-Protocol-Version`, `Mcp-Method`, and `Mcp-Name` on each request.

#### Scenario: 缺失契约头
- **GIVEN** 一个缺少 `MCP-Protocol-Version` 的请求
- **WHEN** 服务端接收
- **THEN** 返回 400 并说明缺失的契约头

### Requirement: Tool Annotations 声明副作用等级
Every tool SHALL declare `readOnlyHint`, `destructiveHint`, `idempotentHint`, and `openWorldHint` annotations.

#### Scenario: 只读工具免确认
- **GIVEN** 一个 `readOnlyHint=true` 且 `destructiveHint=false` 的工具
- **WHEN** 客户端调用
- **THEN** 客户端不弹出破坏性操作确认框，调用一路畅通不打断

### Requirement: 工具参数 Schema 校验
Every tool SHALL provide an `inputSchema` (JSON Schema) and validate arguments before execution.

#### Scenario: 参数缺失
- **GIVEN** 一个缺少必填参数的调用
- **WHEN** 服务端校验
- **THEN** 返回参数错误而非执行工具逻辑

### Requirement: 结构化输出 (MCP Apps)
Tools returning tabular or visual data (e.g., inventory, margin dashboards) SHALL declare `outputSchema` and return `structuredContent` for client rendering.

#### Scenario: 库存看板
- **GIVEN** 查询多仓库存
- **WHEN** 工具返回
- **THEN** 以结构化表格而非纯文本返回，供客户端渲染为看板

### Requirement: server/discover 强制发现
The server SHALL implement `server/discover` for capability advertisement.

#### Scenario: 客户端发现
- **GIVEN** 一个新客户端接入
- **WHEN** 调用 `server/discover`
- **THEN** 返回全部可用工具与能力描述
