## Purpose

AI 自主开发闭环能力：将"隔离执行（沙箱）"与"自动化编排（定时 / 事件 / 自愈）"合并为统一能力——在本地容器化沙箱中自动完成改码、构建、部署、测试、开 PR 的闭环，作为轻量授权之上的纵深防御与开发效率底座。

## ADDED Requirements

### Requirement: 隔离沙箱执行
The system SHOULD run untrusted or high-risk tools and code changes inside an isolated, containerized sandbox with resource quotas (CPU / memory / network egress) and no production credentials mounted.

#### Scenario: 高风险工具执行
- **GIVEN** 一个标记为高风险的工具或一次 agent 自动改码
- **WHEN** 被执行
- **THEN** 在受限沙箱中运行，网络出口与资源受配额约束，且不挂载生产凭证

### Requirement: 熔断与超时
The system SHOULD enforce execution timeouts and circuit breaking for sandboxed runs.

#### Scenario: 超时熔断
- **GIVEN** 沙箱任务超过阈值
- **WHEN** 触发
- **THEN** 强制终止并标记失败，不影响主服务

### Requirement: 数据访问边界
The system SHOULD restrict sandbox access to explicitly granted data scopes with seeded demo data, defaulting to read-only.

#### Scenario: 默认只读
- **GIVEN** 沙箱启动
- **WHEN** 访问数据
- **THEN** 仅能访问授权 / 种子数据范围，且默认只读

### Requirement: 自动化任务编排
The system SHOULD support scheduling and orchestrating agent tasks via timers or events (e.g., daily replenishment check, anomaly scan).

#### Scenario: 定时补货排查
- **GIVEN** 配置了"每日美国站补货排查"任务
- **WHEN** 到达触发时间
- **THEN** Agent 自动执行多表关联分析并产出待补货 SKU 清单

### Requirement: 工作流编排
The system SHOULD allow composing multiple tools / skills into a declarative workflow with branching and retries.

#### Scenario: 异常订单处理流
- **GIVEN** 一个异常订单排查工作流
- **WHEN** 触发
- **THEN** 按既定步骤串联查询、对账、通知，失败步骤自动重试

### Requirement: 自愈与批量执行
The system SHOULD support idempotent batch execution and basic self-healing (retry / fallback) for automation runs.

#### Scenario: 批量重试
- **GIVEN** 一批推送中部分失败
- **WHEN** 自动化重跑
- **THEN** 基于幂等键仅补跑失败项，不重复处理成功项

### Requirement: 闭环交付（改码 → 测试 → PR）
The system SHOULD support an autonomous dev loop where an agent edits code on an isolated branch, builds an image, deploys to an ephemeral sandbox, runs Testcontainers tests, and opens a PR for human merge.

#### Scenario: 自动化改码到测试
- **GIVEN** 一个 OpenSpec 需求
- **WHEN** agent 完成改码并构建
- **THEN** 自动部署到临时沙箱跑测试，通过则开 PR 等人工合并
