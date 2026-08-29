## ADDED Requirements

### Requirement: 用户注册
The system MUST provide a registration endpoint that validates email uniqueness, stores the password only as a BCrypt hash, and never persists or logs plaintext credentials.

#### Scenario: 注册成功
- **GIVEN** 一个未被注册的邮箱
- **WHEN** 提交合法的注册请求（邮箱、密码、确认密码）
- **THEN** 创建状态为启用（非管理员）的用户记录，密码以 BCrypt 哈希存储，返回用户标识且不返回任何凭证字段

#### Scenario: 邮箱重复
- **GIVEN** 一个已注册的邮箱
- **WHEN** 再次提交注册请求
- **THEN** 返回唯一性冲突错误，不创建用户，且不泄露该邮箱是否已存在之外的信息

### Requirement: 用户登录与令牌签发
The system MUST authenticate credentials and issue a short-lived access token plus a longer-lived refresh token upon success.

#### Scenario: 登录成功
- **GIVEN** 一个已注册且状态正常的用户
- **WHEN** 提交正确的邮箱与密码
- **THEN** 签发 access token（短期）与 refresh token（长期），响应中不包含密码哈希

#### Scenario: 凭证错误
- **GIVEN** 一个已注册用户
- **WHEN** 提交错误的密码
- **THEN** 返回统一的认证失败错误（不区分邮箱不存在与密码错误），并记录失败尝试

### Requirement: 会话校验与刷新
The system MUST validate the JWT signature and expiry on every protected request, and MUST allow exchanging a valid refresh token for a new access token.

#### Scenario: 携带有效令牌访问受保护接口
- **GIVEN** 一个未过期的 access token
- **WHEN** 请求受保护的 `/api/v1/**` 接口
- **THEN** 校验签名与有效期通过，请求进入业务逻辑并携带用户上下文

#### Scenario: 令牌过期后刷新
- **GIVEN** 一个已过期但签发合法的 access token 与一个未过期的 refresh token
- **WHEN** 调用刷新接口
- **THEN** 签发新的 access token，旧的 access token 仍按其自身过期时间失效

#### Scenario: 无效或篡改令牌
- **GIVEN** 一个签名不合法或已过期的 refresh token
- **WHEN** 请求受保护接口或刷新接口
- **THEN** 返回 401，不进入业务逻辑

### Requirement: 凭证存储安全
The system MUST NOT store plaintext passwords, and MUST NOT write passwords, tokens or their hashes into application logs.

#### Scenario: 日志脱敏
- **GIVEN** 任何包含密码或令牌字段的请求
- **WHEN** 应用输出访问日志或异常堆栈
- **THEN** 日志中不出现密码、令牌或哈希的明文值
