## Purpose

Liganex 的安全边界与授权范围。本项目定位为实验性、单租户/单用户开源项目，采用轻量授权；本 capability 明确"做了什么、没做什么、何时该补"，使安全范围可审查、可声明（对齐 ADR-0002）。

## ADDED Requirements

### Requirement: 单一 API Key 鉴权
Access to the MCP server SHALL be authenticated by a single API Key combined with a tool whitelist.

#### Scenario: 合法调用
- **GIVEN** 携带有效 API Key 且目标工具在白名单内
- **WHEN** 发起调用
- **THEN** 请求被放行

### Requirement: 不做细粒度授权
The project SHALL NOT implement OAuth 2.1, Token Audience Binding, or Step-Up Authorization in the current version.

#### Scenario: 授权范围边界
- **GIVEN** 评估当前版本的安全能力
- **WHEN** 检查授权机制
- **THEN** 上述细粒度授权明确不在范围内（见 `docs/adr/ADR-0002-auth-scope.md`）

### Requirement: 审计日志
All tool invocations SHALL be recorded in an audit log capturing caller, tool, arguments, result, and timestamp.

#### Scenario: 排障回溯
- **GIVEN** 一次工具调用完成
- **WHEN** 需要复盘或排障
- **THEN** 可从审计日志检索到该次调用的完整记录

### Requirement: 演示用只读数据副本
Write-capable demos SHALL run against a read-only or shadow data copy, never against production ERP data.

#### Scenario: 现场演示
- **GIVEN** 面试官现场演示
- **WHEN** Agent 执行查询或分析
- **THEN** 仅访问只读副本，杜绝误改生产数据导致的演示事故

### Requirement: 写操作人工确认
Destructive or state-mutating tools SHOULD require explicit human confirmation even when their annotation permits execution.

#### Scenario: 写工具调用
- **GIVEN** 一个 `destructiveHint=true` 的工具
- **WHEN** 客户端调用
- **THEN** 弹窗要求人工确认后方可执行
