## ADDED Requirements

### Requirement: 应用创建与凭证签发
The system MUST allow an authenticated user to create an application, generating an `appid` and an `appsecret`, storing only the secret hash and returning the plaintext secret exactly once.

#### Scenario: 创建应用
- **GIVEN** 一个已登录用户
- **WHEN** 提交创建应用请求（名称、描述）
- **THEN** 生成全局唯一的 appid 与随机 appsecret，数据库中仅保存 secret 哈希，响应中返回一次明文 appsecret

#### Scenario: 明文凭证不再可获取
- **GIVEN** 一个已创建的应用
- **WHEN** 后续查询应用详情或列表
- **THEN** 响应中只包含 appid 与凭证掩码，不包含 appsecret 明文

### Requirement: 应用归属与状态管理
The system MUST scope applications to their owner user and MUST support disabling an application so that its credentials stop working immediately.

#### Scenario: 越权访问他人应用
- **GIVEN** 用户 A 的应用
- **WHEN** 用户 B 请求查看或修改该应用
- **THEN** 返回 403 或 404，不泄露该应用是否存在

#### Scenario: 停用应用后凭证失效
- **GIVEN** 一个被停用的应用
- **WHEN** 使用该应用的凭证调用 MCP
- **THEN** 鉴权失败并拒绝调用，即使签名本身正确

### Requirement: 接口权限集绑定
The system MUST maintain a permission dictionary with codes in `{resource}:{action}` form and MUST allow binding a set of permissions to an application.

#### Scenario: 绑定权限集
- **GIVEN** 一个应用与权限字典中的若干权限（如 `order:read`）
- **WHEN** 提交绑定请求
- **THEN** 建立应用与权限的多对多关系，后续校验以该集合为准

#### Scenario: 权限变更即时生效
- **GIVEN** 一个应用的权限集被修改
- **WHEN** 修改提交成功
- **THEN** 主动失效该应用在缓存中的权限快照，下一次调用使用新的权限集

### Requirement: 凭证重置
The system MUST allow regenerating an application secret, invalidating the previous secret immediately.

#### Scenario: 重置 secret
- **GIVEN** 一个已启用的应用
- **WHEN** 用户发起重置凭证
- **THEN** 生成新的 appsecret（同样只返回一次明文），旧 secret 立即失效，新 secret 立即可用
