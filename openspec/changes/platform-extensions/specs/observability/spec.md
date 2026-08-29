## Purpose

可观测能力：让 Agent 与 MCP 调用的行为可追踪、可度量、可解释。本 capability 描述规划中的可观测边界（非立即实现）。

## ADDED Requirements

### Requirement: 调用链追踪
The system SHOULD emit distributed traces covering client → MCP server → tool → ERP data source.

#### Scenario: 端到端追踪
- **GIVEN** 一次 `order-lookup` 调用
- **WHEN** 跨组件执行
- **THEN** 生成含各段耗时的 trace，可定位慢调用

### Requirement: 指标采集
The system SHOULD expose metrics for tool call volume, latency, error rate, and token usage.

#### Scenario: 错误率告警
- **GIVEN** 某工具错误率超过阈值
- **WHEN** 指标被采集
- **THEN** 触发告警供排障

### Requirement: 结构化日志
The system SHOULD emit structured logs that correlate with the existing audit log (caller, tool, args, result, timestamp).

#### Scenario: 日志关联
- **GIVEN** 一次工具调用
- **WHEN** 同时写审计日志与结构化日志
- **THEN** 两者可通过调用 ID 关联回溯
