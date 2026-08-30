## ADDED Requirements

### Requirement: B 端知识与会话按登录用户隔离
知识库、文档、分块、索引任务、会话、消息和记忆 SHALL 归属于经过 JWT 认证的登录用户；系统 MUST 从认证上下文取得用户身份，不得信任请求头、查询参数或请求体自行声明的 owner 或 tenant。

#### Scenario: 检索仅使用当前用户数据
- **WHEN** 登录用户发起知识库检索或智能问答
- **THEN** 系统在所有查询和索引过滤中强制使用认证上下文中的用户标识

#### Scenario: 伪造用户标识
- **WHEN** 请求携带与 JWT 用户不一致的 owner 或 tenant 字段
- **THEN** 系统忽略或拒绝该字段，且不得访问其他用户数据

### Requirement: 模型密钥外部化
Chat 与 Embedding 服务的 API Key MUST 仅由启动环境变量或受控运行参数提供，MUST NOT 出现在仓库文件、接口响应、应用日志、异常消息或审计参数中。

#### Scenario: 缺少模型密钥
- **WHEN** 启用了 RAG 功能但所选模型服务缺少必要密钥
- **THEN** RAG 模块明确报告未就绪且不发送模型请求

#### Scenario: 模型调用失败
- **WHEN** 外部模型调用发生异常
- **THEN** 日志和客户端错误中不包含 API Key、Authorization 头或其他敏感凭证
