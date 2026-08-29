## Why

项目在 baseline 中锁定了架构、协议、数据模型与安全边界。随着演进，规划引入三大平台级能力：AI 自动化（把 Agent 从"被动响应"升级为"主动编排 / 定时 / 自愈"）、可观测（让 Agent 与 MCP 调用的行为可追踪、可度量）、沙箱（让不可信或高风险工具在隔离环境中执行，补齐安全边界）。本变更将这些未来方向固化为**规划态**契约，便于 roadmap 追踪与优先级排序。

## What Changes

新增三个规划中 capability 的源规范：`ai-automation`、`observability`、`sandbox`。本变更仅登记规划，不实现代码。

## Capabilities

### New Capabilities
- `ai-automation`: Agent 自动化——定时 / 事件触发的任务编排、工作流、自愈与批量执行
- `observability`: 可观测——tracing / metrics / structured logging，覆盖 MCP 调用与 Agent 决策链路
- `sandbox`: 沙箱——不可信工具与高风险操作的隔离执行环境、资源配额与熔断

### Modified Capabilities
（无）

## Impact

- 与 `security` capability 协同：沙箱是轻量授权（API Key + 白名单）之上的纵深防御
- 与 `mcp-server` capability 协同：可观测需接入 `server/discover` 与审计日志已有的调用记录
- 当前均为规划态，实现时各自拆分为独立 change（见 tasks 第 2 节）
