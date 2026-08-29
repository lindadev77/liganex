## 1. 工程骨架
- [ ] 1.1 初始化 Maven + Spring Boot 工程（目录、启动类、application.yml）
- [ ] 1.2 引入 MCP Java SDK 或实现 2026-07-28 无状态核心适配层

## 2. 协议端点
- [ ] 2.1 实现 POST /mcp 端点，校验 `MCP-Protocol-Version` / `Mcp-Method` / `Mcp-Name` 头
- [ ] 2.2 按 `Mcp-Method` 路由（tools/call、server/discover 等）

## 3. 工具与发现
- [ ] 3.1 实现 `order-lookup` 工具（inputSchema + annotations + 结构化返回）
- [ ] 3.2 实现中央工具注册表与 `server/discover`

## 4. 安全与可观测
- [ ] 4.1 实现 API Key 鉴权中间件 + 工具白名单
- [ ] 4.2 实现审计日志服务

## 5. 验证
- [ ] 5.1 用现成 MCP 客户端（如 Inspector）连通并成功调用 `order-lookup`
- [ ] 5.2 `openspec validate liganex-mcp-scaffold` 通过
