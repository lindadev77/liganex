## ADDED Requirements

### Requirement: skill 定义与安装
The system MUST ship an installable skill (`order-query`) in the skills repository that declares its trigger scenarios, required configuration (MCP endpoint, appid, appsecret), available tools and conversation examples.

#### Scenario: 客户端发现并安装 skill
- **GIVEN** 一个类 WorkBuddy 的 MCP 客户端
- **WHEN** 用户安装 `liganex-order-query` skill 并配置 MCP endpoint 与凭证
- **THEN** 客户端可发现该 skill 声明的 `query_orders` 工具，并在缺少配置时给出明确的安装前置提示

#### Scenario: 缺少凭证配置
- **GIVEN** skill 已安装但未配置 appid / appsecret
- **WHEN** 用户发起订单相关提问
- **THEN** 返回配置缺失的指引（指向开放平台创建应用），不发起无效调用

### Requirement: 对话式订单查询
The system MUST allow the client to answer natural-language order questions by invoking the MCP tool, with the request signed per the MCP access-control requirements.

#### Scenario: 自然语言查询订单
- **GIVEN** skill 已正确配置且应用持有 `order:read` 权限
- **WHEN** 用户询问「最近一周美国站待发货的订单有多少」
- **THEN** 客户端将意图解析为筛选条件（地区、状态、时间范围）并调用 `query_orders`，结果以对话形式呈现订单概要

#### Scenario: 权限不足时的提示
- **GIVEN** 应用未绑定 `order:read`
- **WHEN** 用户发起订单查询
- **THEN** 将 MCP 返回的权限不足错误转换为可理解的提示，引导用户到开放平台绑定权限集

### Requirement: 接入示例
The system MUST provide runnable signature examples (at least shell and Python) so integrators can reproduce the HMAC signing without reading server source.

#### Scenario: 按示例完成首次调用
- **GIVEN** 用户持有 appid 与 appsecret
- **WHEN** 按文档示例生成签名并调用 MCP 端点
- **THEN** 成功返回订单数据，示例中的签名算法与服务端校验逻辑一致
