## Purpose

Liganex 项目的整体架构、仓库组织与技术栈约束。本 capability 描述系统的分层结构与跨模块协作关系，是其他所有 capability 的上下文基线。

## ADDED Requirements

### Requirement: 四层架构
The system SHALL be organized into four layers: an MCP server (`liganex-mcp`), a client/studio (`liganex-studio`), a skills package (`liganex-skills`), and a hub/registry (`liganex-hub`).

#### Scenario: 端到端数据流
- **GIVEN** 一个跨境 ERP 的查询或操作请求
- **WHEN** 用户经 client/studio 发起
- **THEN** 请求经 MCP server 访问 ERP 数据，能力由 skills 编排，能力清单在 hub 注册并可被发现

### Requirement: 统一仓库前缀
The project SHALL use the `liganex-` prefix for all code repositories, with the root monorepo named `liganex`; frontend code SHALL NOT be a separate repository (it lives under each business repo's `frontend/`).

#### Scenario: 新增业务模块仓库
- **GIVEN** 需要新建一个业务模块仓库
- **WHEN** 在 GitHub 创建
- **THEN** 仓库名遵循 `liganex-<module>`，且前端代码置于该仓 `frontend/` 目录，不单独建仓

### Requirement: Java/Spring Boot 技术栈
The MCP server and hub SHALL be implemented in Java with Spring Boot; client/studio frontend in React or Vue.

#### Scenario: 技术选型
- **GIVEN** 实现 MCP server
- **WHEN** 选择语言与框架
- **THEN** 采用 Java + Spring Boot，以对齐企业 ERP 生态并形成对 TS/Python 主流实现的差异化

### Requirement: 文档与决策记录作为事实来源
The project SHALL maintain Architecture Decision Records under `docs/adr/` and OpenSpec specs under `openspec/specs/` as the source of truth for decisions.

#### Scenario: 决策可追溯
- **GIVEN** 一个关键架构决策被做出
- **WHEN** 需要回溯其理由
- **THEN** 必须能在 ADR 中找到"为什么"，并在对应 OpenSpec spec 中找到"系统必须怎样"
