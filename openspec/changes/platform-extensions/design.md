## Context

baseline 已固定安全边界为轻量授权（API Key + 白名单 + 审计 + 只读副本）。本变更规划两层平台级能力：自主开发闭环让 Agent 能在隔离沙箱中自动改码交付、可观测让行为可见。二者共同把项目从"可调用的 MCP demo"推向"可运营的平台"。

## Goals / Non-Goals

**Goals:**
- 登记 `agent-dev-loop` / `observability` 两个规划中 capability 的契约雏形
- 确立二者与现有 `security` / `mcp-server` / `cross-border-erp-data` / `local-development` 的协同关系与优先级

**Non-Goals:**
- 本变更不实现任何代码
- 不锁定具体技术选型（实现时各自拆分独立 change 再定）

## Decisions

- **合并 sandbox 与 ai-automation 为 `agent-dev-loop`（已采纳）**：用户确认将"隔离执行"与"自动化编排"合并为统一闭环能力。理由：两者运行底座相同（容器化沙箱），且"自动改码 → 测试 → 交付"正是自动化在开发场景的具象，拆分反而割裂。原规划中的熔断 / 超时 / 数据边界 / 任务编排 / 工作流 / 自愈需求整体迁入本 capability。
- 沙箱定位为"轻量授权之上的纵深防御"，而非替代现有 API Key 机制，避免安全范围失控
- 可观测优先复用 baseline 已定义的审计日志字段（caller / tool / args / result / timestamp），降低重复建模
- 自动化强调幂等与自愈，与 `cross-border-erp-data` 中"幂等接收"原则一致

## Risks / Trade-offs

- 工作量差异大：可观测最容易（复用审计）先行，`agent-dev-loop` 最重（需隔离运行时 + 构建部署流水线）后置
- 规划态 spec 描述的是意图，实现前需通过独立 change 细化并重新校验
