## Why

项目在 baseline 中锁定了架构、协议、数据模型与安全边界。随着演进，规划引入平台级能力：AI 自主开发闭环（把 Agent 从"被动响应"升级为"隔离沙箱中自动改码 / 构建 / 测试 / 交付"的闭环，合并原定的 sandbox 与 ai-automation）、可观测（让 Agent 与 MCP 调用的行为可追踪、可度量）。本变更将这些未来方向固化为**规划态**契约，便于 roadmap 追踪与优先级排序。

## What Changes

新增两个规划中 capability 的源规范：`agent-dev-loop`、`observability`。本变更仅登记规划，不实现代码。

## Capabilities

### New Capabilities
- `agent-dev-loop`: AI 自主开发闭环——合并原 sandbox（隔离执行）与 ai-automation（编排 / 定时 / 自愈）为统一能力；在容器化沙箱中自动完成改码、构建、部署、测试、开 PR 的闭环。
- `observability`: 可观测——tracing / metrics / structured logging，覆盖 MCP 调用与 Agent 决策链路。

### Modified Capabilities
（无）

## Impact

- 与 `security` 协同：沙箱是轻量授权（API Key + 白名单）之上的纵深防御
- 与 `mcp-server` 协同：可观测需接入 `server/discover` 与审计日志已有的调用记录
- 与 `local-development`（local-dev-env change）协同：沙箱的运行底座即本地容器化基础设施
- 当前均为规划态，实现时各自拆分为独立 change（见 tasks 第 2 节）
