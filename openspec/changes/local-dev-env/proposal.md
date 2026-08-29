## Why

B 端应用面与 MCP server 进入实现前，需要先固化**本地基础设施选型**与 **AI 自主开发闭环**的运行约定，避免实现阶段各自拍脑袋。本变更将已与用户对齐的本地基础设施（容器运行时、数据库、向量、缓存、消息队列、集成测试）与"AI 改码 → 打包 → 部署沙箱 → 自动化测试"的闭环固化为规划态契约，便于 roadmap 追踪。

## What Changes

新增规划中 capability `local-development`：记录本地基础设施选型与开发闭环约定。本变更仅登记规划与约定，不创建 docker-compose 或编写代码。

## Capabilities

### New Capabilities
- `local-development`: 本地基础设施选型与 AI 自主开发闭环的运行约定（容器运行时、Postgres+pgvector、Redis 8、RocketMQ 5、Testcontainers、沙箱）

### Modified Capabilities
（无）

## Impact

- 与 `mcp-server` / `ai-content-generation` 协同：本地数据库与向量是 MCP 工具与生成后端的依赖
- 与 `agent-dev-loop`（platform-extensions change）协同：沙箱即开发闭环的隔离执行环境，运行底座来自本 capability 的容器化基础设施
- 本变更无对应 ADR；其安全相关部分（沙箱作为纵深防御）与 ADR-0002 轻量授权思路一致
- 当前为规划态，实现时拆分为独立 change（见 tasks 第 2 节）
