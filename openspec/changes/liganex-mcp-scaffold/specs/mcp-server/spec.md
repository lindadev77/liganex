## ADDED Requirements

### Requirement: Maven/Spring Boot 项目骨架
The `liganex-mcp` module SHALL be a Maven project with the standard Spring Boot layout (src/main/java, application.yml, main application class) runnable via `mvn spring-boot:run`.

#### Scenario: 模块启动
- **GIVEN** 初始化 `liganex-mcp`
- **WHEN** 执行 `mvn spring-boot:run`
- **THEN** 服务在配置端口监听且无报错

### Requirement: 无状态 MCP 端点
`liganex-mcp` SHALL expose a single POST endpoint that validates the `MCP-Protocol-Version` / `Mcp-Method` / `Mcp-Name` headers and dispatches by `Mcp-Method`.

#### Scenario: 方法分发
- **GIVEN** 一个 POST 请求携带合法头
- **WHEN** 服务端接收
- **THEN** 按 `Mcp-Method` 路由到对应处理器（tools/call、server/discover 等）

### Requirement: 示例工具 order-lookup
`liganex-mcp` SHALL ship at least one sample tool `order-lookup` with `inputSchema { orderId: string, required }` and `readOnlyHint=true`, `destructiveHint=false`.

#### Scenario: 调用示例工具
- **GIVEN** 调用 `order-lookup` 并传入合法 `orderId`
- **WHEN** 工具执行
- **THEN** 返回该订单的结构化数据且无需破坏性确认

### Requirement: 工具注册与发现
All tools SHALL be registered in a central registry and returned by `server/discover`.

#### Scenario: 发现已注册工具
- **GIVEN** `server/discover` 被调用
- **WHEN** 返回
- **THEN** 响应包含 `order-lookup` 及其 annotations 与 inputSchema

### Requirement: API Key 鉴权中间件
`liganex-mcp` SHALL intercept requests and validate a single API Key against a whitelist of permitted tools.

#### Scenario: 非法 Key
- **GIVEN** 携带错误 API Key
- **WHEN** 请求进入
- **THEN** 返回 401

### Requirement: 审计日志服务
`liganex-mcp` SHALL persist each tool invocation (caller, tool, args, result, timestamp) to an audit store.

#### Scenario: 调用审计
- **GIVEN** 一次工具调用完成
- **WHEN** 落库
- **THEN** 审计记录可被检索用于排障与演示复盘
