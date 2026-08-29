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

### Requirement: 分区表替代分库分表
The system SHOULD use native declarative partitioning (Range by time, List by region) instead of manual sharding, with the partition key included in primary keys and hot query predicates to enable partition pruning.

#### Scenario: 大表按时间/地区切分
- **GIVEN** 订单 / 流水随时间增长、商品 / 库存按地区分布
- **WHEN** 建表与查询
- **THEN** 使用 Range（时间）/ List（地区）分区，分区键进主键与查询条件，旧分区可经 ATTACH/DETACH 在线归档

### Requirement: 索引与表空间冷热分离
The system SHOULD choose index types by access pattern (BRIN for time-series, Partial for hot subsets, GIN for JSONB/arrays) and place hot tables/indexes on fast storage while cold historical partitions use capacity storage via tablespaces.

#### Scenario: 大表索引与冷热分离
- **GIVEN** 一张时序大表与一张带 JSONB 属性的商品表
- **WHEN** 建索引与布局存储
- **THEN** 时序表用 BRIN、热子集用 Partial Index、JSONB 用 GIN，热数据落 NVMe、冷历史分区落大容量盘

### Requirement: PgBouncer 连接池
The system MUST front Postgres with PgBouncer (or equivalent pooler), because PostgreSQL spawns one process per connection and high-concurrency short connections must be pooled.

#### Scenario: 高并发短连接
- **GIVEN** 业务系统高频短连接访问数据库
- **WHEN** 连接建立
- **THEN** 经 PgBouncer 池化，避免连接数膨胀拖垮实例

### Requirement: Citus 横向扩展路径（触发式）
When a single Postgres instance hits capacity or write-throughput limits that partitioning, indexing, and read replicas cannot resolve, the system SHOULD adopt Citus for automatic sharding (SQL-compatible) rather than building custom sharding middleware.

#### Scenario: 单实例触顶
- **GIVEN** 单实例已无法通过分区 / 索引 / 只读副本消解负载
- **WHEN** 需要横向扩展
- **THEN** 引入 Citus 自动分片，不自建分库分表路由 / 双写 / 跨分片 join 拼接

### Requirement: autovacuum 运维调优
The system MUST tune autovacuum (frequency, thresholds, cost limit) and manage transaction-id freezing on large tables to prevent bloat and wrap-around, leveraging per-partition vacuum/analyze for low maintenance cost.

#### Scenario: 大表膨胀防控
- **GIVEN** 持续增长的大表
- **WHEN** 运行维护
- **THEN** autovacuum 按表调优并管理冻结，分区表各自独立 vacuum/analyze

### Requirement: Schema 迁移版本化管理
The system SHOULD manage all schema changes via versioned migrations (Flyway), where each structural change is an incremental, append-only SQL file (V__ prefix) rather than editing the initial CREATE TABLE or hand-running ALTER out-of-band; migrations are version-controlled and run in CI and integration tests.

#### Scenario: 迭代新增字段
- **GIVEN** 一次迭代需要在订单表新增一个字段
- **WHEN** 变更表结构
- **THEN** 新增一个 `V{n}__add_column.sql` 增量迁移文件，而非修改历史初始化 SQL；CI 与 Testcontainers 集成测试经迁移重建一致 schema

#### Scenario: ORM 不自动建表
- **GIVEN** 应用使用 MyBatis-Plus 做数据访问
- **WHEN** 启动与部署
- **THEN** Flyway 为 schema 真相源（管 DDL），禁用 `ddl-auto=update` 之类 ORM 自动建表，避免双源冲突

### Requirement: 前端本地开发经 nginx 反向代理规避跨域
The frontend SHOULD integrate with backend APIs through a local nginx reverse proxy during development (forwarding an `/api` prefix to the backend), rather than enabling permissive CORS or hardcoding backend origins; this keeps dev and prod edge topology consistent (see ADR-0006) and avoids browser CORS in local debugging.

#### Scenario: 本地联调规避跨域
- **GIVEN** 前端在本地开发、后端运行于另一端口或主机
- **WHEN** 发起接口请求
- **THEN** 前端请求同源（经本地 nginx 的 `/api` 前缀），由 nginx 转发到后端，浏览器不产生跨域，且不开启宽松 CORS

#### Scenario: 与部署边缘一致
- **GIVEN** 生产由 nginx 做部署级反向代理（ADR-0006）
- **WHEN** 本地开发配置代理
- **THEN** 本地复用同一 nginx 转发形态（仅后端地址不同），dev 与 prod 拓扑一致，减少环境差异
