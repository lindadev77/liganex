## Context

实现阶段前需统一本地基础设施。已与用户对齐：48G 内存笔记本可本地起 db / 向量 / redis / mq 做测试；偏向容器化以获得可丢弃的隔离环境；一次性离线数据处理习惯用 Python 脚本；本地容器化顺理成章延伸到"AI 改码打包部署跑自动化测试不需人工干预"的开发闭环。

## Goals / Non-Goals

**Goals:**
- 固化本地基础设施选型与版本
- 固化 AI 自主开发闭环的运行约定（沙箱形态、种子数据、不挂生产凭证）

**Non-Goals:**
- 本变更不创建 docker-compose / 不编写任何代码
- 不锁定云上部署形态（实现时再定）
- 一次性离线数据处理脚本不纳入本 capability 的工程边界（脚本只做数据搬运 / 清洗，不承载业务规则）

## Decisions

### 本地基础设施选型
- **容器运行时**：OrbStack 管理容器（非 Docker Desktop），降低 macOS 上资源占用与后台开销。
- **数据库 + 向量**：Postgres + pgvector。需要 SQL 一致性的业务数据（订单 / 商品 / 库存）与向量检索共用一套存储，避免双写一致性问题。
- **缓存 / 会话 / 限流**：Redis 8。其 Query Engine 特性（语义缓存、agent 会话状态、限流计数）与 AI 场景契合，后续大概率依赖。
- **消息队列**：RocketMQ 5。事务消息 / 延迟消息 / 顺序消息契合结算与订单超时等跨境 ERP 场景；ARM64 镜像可用，可在本地跑。
- **集成测试**：Testcontainers 拉起一致的 PG / Redis / RocketMQ 做集成测试（48G 内存余量充足）。

### AI 自主开发闭环（沙箱）
- 闭环：OpenSpec 需求 → agent 改码（隔离分支） → 构建镜像 → 部署临时沙箱 → Testcontainers 测试 → 通过开 PR（人工合并）。
- 沙箱定义：容器化、带种子数据、不挂载生产凭证。
- 沙箱是 ADR-0002 轻量授权之上的纵深防御，也是 platform-extensions 中 `agent-dev-loop` 能力的运行底座。

### 存储扩展策略（单机 → 横向）

基于 ADR-0004，本地与实现阶段统一遵循以下存储约定：

- **分区表替代分库分表**：订单 / 流水按时间 Range 分区，商品 / 库存按地区 List 分区；分区键进入主键与高频查询条件，利用分区裁剪只扫命中分区，旧分区经 ATTACH/DETACH 在线归档。
- **索引与表空间**：时序 / 日志类大表优先 BRIN；热子集用 Partial Index；JSONB / 数组用 GIN；热表 + 热索引放 NVMe，冷历史分区挂大容量盘（表空间冷热分离）。
- **PgBouncer 必配**：PG 每连接一进程，高并发短连接必须靠连接池补，本地与部署态都应前置。
- **横向扩展走 Citus**：单实例写吞吐 / 容量触顶时上 Citus 自动分片，不自建分库分表；Greenplum（分析）/ 云托管（Aurora PG / PolarDB）为备选。
- **autovacuum 调优为运维硬要求**：大表必调频 / 阈值 / cost limit，并管理事务 ID 冻结防回卷。

## Risks / Trade-offs

- OrbStack 与 Apple Silicon 偶有兼容波动，需锁定版本。
- RocketMQ 本地跑需容器资源，48G 内存可覆盖但需控制同时运行的实例数。
- 一次性离线处理虽用 Python，但项目主栈是 Java / Spring Boot，脚本与工程需明确边界（脚本只做数据搬运 / 清洗，不承载业务规则）。
