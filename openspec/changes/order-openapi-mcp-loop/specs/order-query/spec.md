## ADDED Requirements

### Requirement: 订单查询领域能力
The system MUST provide an order query capability supporting pagination and filtering by region, order status and creation time range, implemented as an independent domain module.

#### Scenario: 多条件分页查询
- **GIVEN** 存在覆盖多个地区与多种状态的订单数据
- **WHEN** 以「地区 + 状态 + 时间范围 + 分页参数」查询
- **THEN** 返回符合条件的订单分页结果，包含订单号、地区、状态、金额、下单时间等字段，总数与分页信息正确

#### Scenario: 空结果
- **GIVEN** 筛选条件无匹配数据
- **WHEN** 执行查询
- **THEN** 返回空列表与总数 0，不抛异常

### Requirement: 前端订单查询接口
The system MUST expose `/api/v1/orders/*` for the B-end frontend, authenticated by user JWT.

#### Scenario: 已登录用户查询订单
- **GIVEN** 一个有效 access token
- **WHEN** 调用 `/api/v1/orders` 并携带筛选参数
- **THEN** 返回该用户可见的订单分页数据

#### Scenario: 未认证访问
- **GIVEN** 未携带或携带无效 token
- **WHEN** 调用 `/api/v1/orders`
- **THEN** 返回 401，不查询数据库

### Requirement: 内部订单查询接口
The system MUST expose `/internal/v1/orders/*` for internal services (`liganex-mcp`), authenticated by a service-to-service API key, and MUST NOT allow this path to be reached with a user JWT.

#### Scenario: MCP 携带内部凭证查询
- **GIVEN** `liganex-mcp` 持有有效的内部服务凭证
- **WHEN** 调用 `/internal/v1/orders` 查询订单
- **THEN** 返回订单数据，响应结构与前端接口共用同一份 DTO 契约

#### Scenario: 内部接口拒绝用户令牌
- **GIVEN** 一个有效的用户 access token
- **WHEN** 调用 `/internal/v1/orders`
- **THEN** 返回 403，不进入业务逻辑

### Requirement: 订单模块边界
The order module MUST expose only DTOs and MUST NOT leak persistence entities, MyBatis objects or internal exceptions to callers; consumers MUST depend on the `OrderQueryClient` abstraction rather than on module internals.

#### Scenario: 消费方通过接口抽象访问
- **GIVEN** 一个需要订单数据的消费方（前端控制器或 MCP tool）
- **WHEN** 其发起查询
- **THEN** 仅依赖 `OrderQueryClient` 接口，本地实现与远程实现可由配置切换，消费方代码无需修改

#### Scenario: 禁止跨服务直连订单库
- **GIVEN** 任何非订单模块所在的服务（含 `liganex-mcp`）
- **WHEN** 需要订单数据
- **THEN** 必须经由内部服务接口获取，不得直接连接订单数据库

### Requirement: 订单表分区策略
Order tables MUST be partitioned by creation time (RANGE) and region (LIST) per ADR-0004, with the partition key present in the primary key and in query predicates.

#### Scenario: 查询命中分区裁剪
- **GIVEN** 订单表按时间与地区分区
- **WHEN** 查询携带时间范围与地区条件
- **THEN** 执行计划仅扫描命中的分区，不扫描全部分区
