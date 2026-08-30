## Context

See `proposal.md` for motivation. Studio 当前是 React 单页应用 + Spring Boot 模块化单体，已具备 JWT 用户身份、Postgres/Redis、本地 Docker 基础设施与 Flyway/MyBatis-Plus。旧 `qdrant-langchain4j` 项目提供了父子分块、中文词项、RRF、租户过滤、引用和 SSE 的可参考实现，但其 Qdrant + 进程内 BM25 双索引不能直接满足本变更的一致性与多实例要求。

本地数据库镜像已包含 pgvector，但当前迁移尚未创建 `vector` 扩展。RAG 模型通过 OpenAI 兼容接口调用；用户提供的 OpenRouter 地址与模型名仅作为运行参数，真实 API Key 不进入任何仓库文件。

## Goals / Non-Goals

**Goals:**

- 让前后端可依据稳定 API 契约并行实现知识库管理和智能问答。
- 以 Postgres 为知识原文、分块、索引状态、会话和消息的事实来源。
- 本期完成 pgvector 混合检索，同时让业务层不依赖具体向量数据库 SDK。
- 从类型、服务查询和数据库约束三个层面强制 JWT 用户隔离。
- 通过持久历史、最近窗口和滚动摘要实现可恢复的分层记忆。
- 让模型密钥只存在于进程环境，并对配置、日志和提交进行泄密防护。

**Non-Goals:**

- 不为未实现的 Qdrant/Redis 提供空适配器或静默回退。
- 不做跨后端在线双写、自动数据复制和无重建热切换。
- 不把 Redis 缓存实例兼作知识向量主索引。
- 不在本期引入独立 RAG 服务、对象存储、OCR、网页抓取或长期用户事实记忆。

## Decisions

### 1. 模块化单体内新增四个清晰边界

后端在 `liganex-studio-backend` 内新增：

- `module.knowledge`：知识库、文档、原文、处理任务和 CRUD。
- `module.rag`：解析、父子分块、Embedding、混合检索、引用组装。
- `module.chat`：会话、消息、SSE 与分层记忆。
- `module.ai`：OpenAI 兼容 Chat/Embedding 模型配置和适配。

模块通过 DTO/port 交互，不暴露 MyBatis entity。当前部署物不增加；后续只有索引任务出现独立扩缩需求时才提取 worker。

### 2. Postgres 是事实来源，pgvector 是本期索引实现

新增 Flyway 迁移创建 `vector` 扩展和以下核心表：

| 表 | 责任 |
|---|---|
| `knowledge_base` | 用户知识库 |
| `knowledge_document` | 文档元数据、抽取文本、状态、校验值 |
| `knowledge_document_blob` | 首期不超过配置上限的原始文件字节 |
| `knowledge_chunk` | 父子分块、正文、词项、`vector(1536)`、引用位置 |
| `knowledge_index_job` | 可重试解析/索引/删除任务 |
| `knowledge_document_index` | 后端、模型、维度、版本和构建状态 |
| `chat_conversation` | 用户会话与知识库选择 |
| `chat_message` | 完整原始消息、状态、引用快照 |
| `chat_summary` | 滚动摘要及覆盖消息位置 |

首期文件大小受限，原始文件和抽取文本均保存在 Postgres，使删除可通过外键级联在单事务内完成。引入对象存储时再通过 Outbox 清理外部对象。

`owner_user_id` 出现在知识库、文档、分块、任务、会话和消息的高频访问路径中；服务查询始终包含该条件。必要的组合唯一键/外键保证文档、知识库与 owner 关系不能串接。

选择 pgvector 的原因：符合 ADR-0004、无需新增部署物、向量与业务数据同库、删除可原子化。Qdrant 的独立扩缩和 Redis 的低延迟不是当前规模的主要矛盾。

### 3. 只在索引边界使用端口/适配器

定义后端无关的 `KnowledgeIndex` 端口，语义覆盖：

- 幂等批量写入稳定 `chunkId`
- 强制 `ownerUserId + knowledgeBaseIds` 的混合检索
- 按用户/知识库/文档过滤删除
- 健康和维度校验
- 返回统一的 rank、chunkId、content、source、metadata 与可选 backendScore

本期注册唯一 `PgVectorKnowledgeIndex`。通过 `liganex.rag.index.backend` 条件装配；配置为当前构建未实现的后端时 fail-fast。业务层不得出现后端名称分支。

LangChain4j 的模型、分块和通用数据结构可复用，但 pgvector 混合 SQL 由项目控制，以便复用 Flyway 表结构、事务、组合用户过滤和删除级联，而不是让 SDK 自动建表。

### 4. 切换后端是“配置选择 + 显式重建”，不是只改配置

每份索引记录保存 backend、embedding model、dimension、index version 和状态。未来切换过程：

1. 提供目标 adapter 与基础设施；
2. 从 Postgres 原文/分块重建目标索引；
3. 校验数量、维度、隔离和抽样检索；
4. 将读取 backend 配置切到目标；
5. 观察后再清理旧索引。

目标未 READY 时禁止切换。重建失败继续使用原索引。首期不实现双写，但数据模型允许同一文档拥有多个 backend/version 状态。

### 5. pgvector 混合检索由两路候选 + RRF 组成

入库流程：解析 -> 父子分块 -> 批量 Embedding -> 保存 chunk/embedding -> 标记 READY。每个子块保存父块正文或父块引用，用子块召回、父块提供模型上下文。

检索 SQL 在 CTE 中分别执行：

- Dense：在 owner 和知识库过滤内按 cosine distance 取候选；
- Lexical：对持久化 `tsvector` 使用全文检索排名取候选；
- Fusion：按 chunkId 使用 RRF 融合，再取最终候选；
- Context：按父块去重并组装引用。

中文词项复用旧项目的汉字 unigram/bigram 与拉丁词规范化逻辑，在入库和查询时生成空格分隔词项，再由 Postgres `simple` 配置建立 `tsvector`，避免进程内 BM25 和启动全量重建。

数据量较小时保留精确向量检索；达到压测阈值后创建 HNSW cosine 索引并开启/调优带过滤的迭代扫描。索引参数必须通过基准测试决定。

### 6. 文档处理使用持久任务表，不依赖内存队列

上传请求只落库并返回文档/任务状态。后台 worker 以数据库任务表领取任务，使用状态与锁字段防重复执行；任务具有 retry count、next retry time 和 error summary。这样进程重启后任务不会丢失，也无需本期增加 RocketMQ 依赖。

幂等键由 documentId、content hash、embedding model/version 构成。重复任务 upsert 相同 chunkId，不产生重复索引。

### 7. 用户隔离由认证上下文驱动

Controller 只从 `@AuthenticationPrincipal` 获取用户 ID；任何 API 均不接受可信的 owner/tenant 字段。所有知识库 ID 和会话 ID 在进入模型、索引或任务前先校验归属。

索引请求对象在构造时必须包含 ownerUserId；不提供无 owner 的检索重载。跨用户资源统一表现为不存在，降低枚举风险。集成测试以两个用户、相同标题/相似正文验证查询与删除不串数据。

Postgres RLS 留作后续纵深防御；首期依靠组合约束和强制查询条件，避免在共享连接池中错误设置 session tenant 带来的新风险。

### 8. 完整历史与模型记忆分层保存

- L0：`chat_message` 保存完整、不可被摘要覆盖的 UI 历史。
- L1：每次调用按 Token 预算读取最近消息，组装短期工作记忆。
- L2：超过阈值后异步生成滚动摘要，记录覆盖到的 message sequence。

模型上下文顺序为：系统约束 -> 会话摘要 -> 最近窗口 -> 检索上下文 -> 当前问题。检索上下文不写回工作记忆；引用快照写入助手消息。Redis 只用于同一 conversation 的短锁/取消标记，避免并发生成打乱顺序。

### 9. 前端以两个一级菜单并行实现

`MainLayout` 改为路由驱动菜单和标题：

- `/knowledge/bases`：知识库列表、创建/编辑/删除。
- `/knowledge/bases/:id`：文档上传、文本录入、状态、重试、删除。
- `/chat`：会话列表、知识库多选、消息流、引用和停止生成。

流式请求使用 `fetch` + `ReadableStream`，因为 POST、Bearer Token 和请求体不适合原生 EventSource。前端根据 token/done/error 事件更新消息，完成事件持久化引用。

前后端以 OpenSpec 场景和 API DTO 为契约并行开发；前端先用类型化 mock 验证页面，联调时替换为真实 API。

### 10. 模型配置完全外部化

手动装配 LangChain4j Chat/Embedding 客户端，不使用可能与 Spring Boot 4 不匹配的自动配置 starter。运行时变量：

- `LIGANEX_EMBEDDING_BASE_URL`
- `LIGANEX_EMBEDDING_API_KEY`
- `LIGANEX_EMBEDDING_MODEL_NAME`
- `LIGANEX_EMBEDDING_DIMENSIONS`
- `LIGANEX_EMBEDDING_TIMEOUT_MS`
- `LIGANEX_EMBEDDING_MAX_RETRIES`
- `LIGANEX_CHAT_BASE_URL`
- `LIGANEX_CHAT_API_KEY`
- `LIGANEX_CHAT_MODEL_NAME`
- `LIGANEX_CHAT_TIMEOUT_MS`
- `LIGANEX_CHAT_MAX_RETRIES`

本地联调可在启动进程中把 base URL 指向 OpenRouter，并选择用户给定的免费 Embedding/Chat 模型；仓库只保存变量引用和非敏感结构，不保存实际值。启动时探测必要配置和 embedding 维度；缺失或不匹配时 RAG readiness 为 DOWN。所有 HTTP 日志对 Authorization、API Key 和请求头脱敏。

## Risks / Trade-offs

- [免费模型限流、下线或响应格式变化] -> 模型适配器设置超时/重试，返回明确错误，测试不依赖在线模型作为唯一验证。
- [用户给定 Embedding 服务实际维度与配置不一致] -> 启动/首次调用校验向量长度，维度不匹配时禁止写入。
- [PDF 解析质量和恶意文件] -> 限制 MIME/大小/页数，解析失败可见，不执行上传文件中的任何代码。
- [中文全文检索精度有限] -> 首期使用可测试的 unigram/bigram 词项与 RRF；以固定评测集调参，后续再评估专业分词扩展。
- [HNSW 带用户过滤时召回不足] -> 小数据先精确检索；启用 HNSW 后使用候选扩展与迭代扫描并做双用户召回测试。
- [数据库保存原始文件导致容量增长] -> 首期限制文件大小并用于本地演示；达到容量阈值后引入对象存储与 Outbox。
- [后端抽象过度] -> 只抽象索引端口，本期只实现 pgvector，不为未来后端预写业务逻辑。
- [配置切换造成空索引] -> 读取后端只能指向 READY 的索引版本，切换前必须运行重建和校验。
- [密钥已在外部渠道暴露] -> 不写仓库、不复述、不记录；联调后撤销并轮换，提交前执行秘密扫描。

## Migration Plan

1. 轮换已暴露的开发密钥，并仅在本地启动环境中设置新值。
2. 通过 Flyway 增量迁移创建扩展、知识库/索引/会话表与约束；保留现有业务表。
3. 部署后端但默认关闭 RAG readiness，完成模型和维度探测后启用。
4. 部署前端菜单和页面；旧“我的应用”路由保持兼容。
5. 创建两个测试用户，完成上传、索引、混合检索、流式回答、摘要与跨用户隔离冒烟。
6. 删除测试知识库，验证分块/向量级联清理且历史引用快照仍可显示。

回滚时隐藏新菜单、停止索引 worker 并回退应用版本；新增表暂时保留以防数据丢失，由后续 Flyway 迁移显式清理，绝不回改历史迁移。

## Open Questions

- PDF 首期最大文件大小和页数可在实现前按本地模型吞吐选择非破坏性默认值。
- 会话摘要触发阈值、最近窗口 Token 预算和 RRF 候选数由配置提供，最终数值通过固定评测集调整。
