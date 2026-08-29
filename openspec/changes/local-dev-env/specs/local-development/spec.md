## Purpose

本地开发基础设施与 AI 自主开发闭环的运行约定。本 capability 描述规划中的本地选型边界（非立即实现），作为实现阶段 docker-compose 与测试骨架的事实基线。

## ADDED Requirements

### Requirement: 容器化本地基础设施
The development environment SHOULD run databases, vector store, cache, and message queue as containers managed by OrbStack (not Docker Desktop), to provide disposable, isolated local infrastructure.

#### Scenario: 本地起全套依赖
- **GIVEN** 开发者克隆仓库后准备联调
- **WHEN** 启动本地环境
- **THEN** 通过 OrbStack 拉起 Postgres+pgvector、Redis 8、RocketMQ 5，无需手动安装各组件

### Requirement: 统一存储与向量
The system SHOULD use Postgres with the pgvector extension as the single store for both relational business data (orders / products / inventory) and vector search, avoiding dual-write consistency issues.

#### Scenario: 向量与业务数据同源
- **GIVEN** 需要同时做订单查询与语义检索
- **WHEN** 读写数据
- **THEN** 统一走 Postgres+pgvector，不引入独立向量库造成双写

### Requirement: Redis 8 作为 AI 会话与限流层
The system SHOULD use Redis 8 as the cache, agent session store, and rate limiter, leveraging its Query Engine capabilities for semantic cache and session state.

#### Scenario: 限流与语义缓存
- **GIVEN** 高频 AI 生成请求
- **WHEN** 进入生成后端
- **THEN** 经 Redis 8 做限流与语义缓存，命中则短路返回

### Requirement: RocketMQ 5 处理事务与延迟消息
The system SHOULD use RocketMQ 5 for transactional, delayed, and ordered messages, fitting cross-border settlement and order-timeout scenarios.

#### Scenario: 订单超时与结算
- **GIVEN** 一笔待结算订单
- **WHEN** 触发超时或结算事件
- **THEN** 经 RocketMQ 5 事务 / 延迟消息可靠投递，不丢不重

### Requirement: 容器化集成测试
The system SHOULD run integration tests via Testcontainers, spinning up consistent PG / Redis / RocketMQ instances.

#### Scenario: 一致环境集成测试
- **GIVEN** 一次 CI 集成测试
- **WHEN** 执行
- **THEN** Testcontainers 拉起与生产一致的中间件版本，避免环境漂移

### Requirement: AI 自主开发闭环沙箱
The system SHOULD support an AI autonomous dev loop: OpenSpec requirement → agent edits on isolated branch → build image → deploy to ephemeral sandbox (containerized, seeded data, no production credentials) → Testcontainers tests → open PR for human merge.

#### Scenario: 自动化改码到测试
- **GIVEN** 一个 OpenSpec 需求
- **WHEN** agent 完成改码并构建
- **THEN** 自动部署到临时沙箱，跑 Testcontainers 测试，通过则开 PR 等人工合并，全程不挂生产凭证
