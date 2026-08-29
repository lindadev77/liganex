## Context

baseline 已固定安全边界为轻量授权（API Key + 白名单 + 审计 + 只读副本）。本变更规划三层平台级能力：自动化让 Agent 主动做事、可观测让行为可见、沙箱让高风险执行可控。三者共同把项目从"可调用的 MCP demo"推向"可运营的平台"。

## Goals / Non-Goals

**Goals:**
- 登记 `ai-automation` / `observability` / `sandbox` 三个规划中 capability 的契约雏形
- 确立三者与现有 `security` / `mcp-server` / `cross-border-erp-data` 的协同关系与优先级

**Non-Goals:**
- 本变更不实现任何代码
- 不锁定具体技术选型（实现时各自拆分独立 change 再定）

## Decisions

- 沙箱定位为"轻量授权之上的纵深防御"，而非替代现有 API Key 机制，避免安全范围失控
- 可观测优先复用 baseline 已定义的审计日志字段（caller / tool / args / result / timestamp），降低重复建模
- 自动化强调幂等与自愈，与 `cross-border-erp-data` 中"幂等接收"原则一致

## Risks / Trade-offs

- 三者工作量差异大：可观测最容易（复用审计）先行，沙箱最重（需隔离运行时）后置
- 规划态 spec 描述的是意图，实现前需通过独立 change 细化并重新校验
