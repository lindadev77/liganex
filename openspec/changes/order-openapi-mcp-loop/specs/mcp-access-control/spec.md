## ADDED Requirements

### Requirement: 应用签名校验
The system MUST authenticate MCP callers by HMAC-SHA256 signature over `appid + timestamp + nonce + method + path + body hash`, and MUST NOT accept the app secret transmitted in plaintext.

#### Scenario: 合法签名通过
- **GIVEN** 一个启用状态的应用及其 appsecret
- **WHEN** 携带正确签名且在时间窗内调用 MCP 端点
- **THEN** 校验通过，进入权限校验环节

#### Scenario: 时间戳超出窗口
- **GIVEN** 一个签名正确但时间戳早于 5 分钟前的请求
- **WHEN** 调用 MCP 端点
- **THEN** 拒绝请求并返回时间戳失效错误

#### Scenario: 重放请求被拦截
- **GIVEN** 一个已被使用过的 nonce
- **WHEN** 以相同 nonce 再次发起请求
- **THEN** 拒绝请求（Redis SET NX 失败），不执行业务逻辑

#### Scenario: 签名不匹配
- **GIVEN** 使用错误 appsecret 计算出的签名
- **WHEN** 调用 MCP 端点
- **THEN** 拒绝请求并返回签名校验失败，不泄露期望签名值

### Requirement: 权限集校验
The system MUST declare the required scope for each MCP tool and MUST reject a call when the application does not hold that scope.

#### Scenario: 持有所需权限
- **GIVEN** 应用绑定了 `order:read`
- **WHEN** 调用声明 `order:read` 的 `query_orders` tool
- **THEN** 校验通过并执行工具

#### Scenario: 缺少所需权限
- **GIVEN** 应用未绑定 `order:read`
- **WHEN** 调用 `query_orders` tool
- **THEN** 拒绝执行并返回权限不足的结构化错误，错误中不列出应用已拥有的全部权限

### Requirement: 校验缓存
The system MUST cache the application snapshot (`secret_hash`, `scopes`, `status`) in Redis with a short TTL and MUST invalidate it proactively on permission or status change.

#### Scenario: 缓存命中
- **GIVEN** 应用快照已缓存且未过期
- **WHEN** 校验阶段读取应用信息
- **THEN** 从 Redis 读取，不查询主库

#### Scenario: 权限变更后失效
- **GIVEN** 应用权限集或状态发生变更
- **WHEN** 变更提交成功
- **THEN** 对应缓存键被主动失效，下次校验回源获取最新快照

### Requirement: 配额与调用审计
The system MUST count application calls against a quota with soft and hard limits (ADR-0003), and MUST record an audit entry for every call (ADR-0002).

#### Scenario: 超出硬限额
- **GIVEN** 应用当期用量达到硬限额
- **WHEN** 再次发起调用
- **THEN** 拒绝调用并返回配额耗尽错误，同时记录审计日志

#### Scenario: 审计留痕
- **GIVEN** 一次 MCP 调用（无论成功或失败）
- **WHEN** 调用结束
- **THEN** 记录应用、tool、权限码、时间、结果、耗时，且日志中不包含 appsecret 明文

### Requirement: 内部调用韧性
The system MUST configure a timeout (≤ 2s), retry and fallback for calls from MCP to the internal order interface, and a failure MUST NOT take down the MCP server or affect other tools.

#### Scenario: 订单接口超时
- **GIVEN** 内部订单接口响应超过超时阈值
- **WHEN** MCP 调用订单数据
- **THEN** 中断等待并返回明确的不可用错误，MCP 进程保持可用，其他 tool 不受影响

#### Scenario: 订单接口异常
- **GIVEN** 内部订单接口返回 5xx
- **WHEN** MCP 调用订单数据
- **THEN** 按重试策略有限重试后降级返回错误，并将失败原因记入审计日志
