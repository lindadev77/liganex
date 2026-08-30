## Why

Liganex Studio 目前只有开放平台应用管理，缺少面向登录用户的知识库维护与智能问答闭环。项目需要在既有 Postgres、Redis、JWT 和模块化单体基础上补齐可追溯、可删除、按账号隔离的 RAG 能力，并为后续切换 Qdrant 或 Redis 向量索引保留稳定边界。

## What Changes

- 在 Studio 单一前端入口新增一级菜单“智能问答”和“知识库管理”。
- 新增知识库、文档、分块、索引任务的数据模型与管理 API，支持文本及首期文件类型的异步解析、向量化、状态查看、重试和删除。
- 本期使用 Postgres + pgvector 保存分块、向量和全文检索数据，通过混合检索、RRF、父子分块和引用返回完成 RAG 闭环。
- 定义知识索引端口与按配置选择的适配器机制，本期仅实现 pgvector；Qdrant/Redis 作为后续实现，切换时必须先重建并校验索引，不能仅修改配置跳过数据迁移。
- 新增会话、消息、最近窗口和滚动摘要组成的分层记忆；完整历史与模型工作记忆分开持久化。
- 知识库、文档、索引、会话、消息和检索结果全部按 JWT 登录用户隔离；请求不得自行声明可信的 owner/tenant 身份。
- AI Chat 与 Embedding 使用 OpenAI 兼容接口，真实 API Key 只能通过启动环境变量或参数注入，不得写入仓库文件、日志或提交记录。
- 删除知识库或文档时立即停止检索，并一致地清理原文引用、分块和向量；外部对象存储清理通过可重试任务完成。

## Capabilities

### New Capabilities

- `knowledge-base-management`: 登录用户维护知识库与文档，跟踪解析/索引状态并可靠删除或重建内容。
- `rag-question-answering`: 在用户授权的知识库范围内执行混合检索、流式问答并返回可追溯引用。
- `conversation-memory`: 持久化完整会话历史，并按最近窗口与滚动摘要构造分层模型记忆。
- `vector-index-management`: 以稳定端口管理索引写入、过滤检索和删除，本期实现 pgvector，并支持后续可控迁移到 Qdrant/Redis。

### Modified Capabilities

- `security`: 将 B 端知识库与智能问答的数据访问明确为按 JWT 登录用户隔离，并增加跨用户访问拒绝要求。

## Impact

- 后端：`server/liganex-studio-backend` 新增 knowledge/chat/index/model 模块、Flyway 增量迁移、LangChain4j 与 pgvector 依赖、SSE 接口及异步索引任务。
- 前端：`studio-frontend` 扩展导航、知识库管理页、文档状态页和智能问答页，并接入流式响应与引用展示。
- 本地基础设施：复用现有 Postgres/pgvector 与 Redis；本期不新增 Qdrant，不与现有缓存 Redis 混用知识向量索引。
- 配置：新增 `LIGANEX_EMBEDDING_*`、`LIGANEX_CHAT_*` 等环境变量占位，仓库配置仅保存空值或非敏感默认值。
- ADR：关联 ADR-0003（B 端租户边界）、ADR-0004（Postgres + pgvector）、ADR-0005（Flyway）、ADR-0006（模块化单体）、ADR-0007（密钥外部化）、ADR-0008（React/Ant Design X）；实现前新增向量索引与 RAG 数据一致性 ADR。

## Non-goals

- 本期不实现 Qdrant 或 Redis 向量适配器、不做多后端双写与运行时无迁移热切换。
- 不实现团队空间、知识库分享、RBAC、公开链接或跨用户知识检索。
- 不实现网页爬取、OCR、复杂 Office 文件解析和长期用户事实记忆。
- 不实现独立 RAG 微服务、API 网关、完整计费或模型自动择优路由。
- 不把真实模型密钥、用户上传原文或对话内容提交到 GitHub。
