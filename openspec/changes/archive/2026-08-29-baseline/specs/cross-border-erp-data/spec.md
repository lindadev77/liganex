## Purpose

跨境 ERP 领域数据模型与已知数据质量风险的处理约束。本 capability 把真实的跨境电商业务理解（多平台 SKU 映射、多仓库存、跨境头程时效、汇率结算、退货冲回）固化为系统必须遵循的数据契约，是本项目相对"通用 Agent demo"的核心差异化来源。

## ADDED Requirements

### Requirement: 三大核心数据域
The system SHALL model three core domains: orders, products (SKU with multi-platform mapping), and inventory (multi-warehouse).

#### Scenario: 数据域覆盖
- **GIVEN** 一个跨境 ERP 实例
- **WHEN** 进行数据建模
- **THEN** 必须包含订单、商品（SKU 多平台映射）、库存（多仓）三类实体及其关系

### Requirement: 时区一致处理
Order timestamps SHALL be normalized to a single canonical timezone before any cross-site aggregation.

#### Scenario: 跨站点按日汇总
- **GIVEN** 美国站与德国站的订单
- **WHEN** 按日汇总销量
- **THEN** 时间统一换算到同一时区后再聚合，避免日期错位

### Requirement: 币种与汇率分离
Settlement currency and accounting currency SHALL be modeled as distinct fields; profit MUST be computed in accounting currency using the applicable exchange rate.

#### Scenario: 利润核算
- **GIVEN** 一笔多币种销售
- **WHEN** 计算利润
- **THEN** 按记账币种与适用汇率换算，明确区分结算币种与记账币种

### Requirement: 库存口径分类
Inventory SHALL be modeled with distinct states: available, in-transit, locked, defective.

#### Scenario: 可用量计算
- **GIVEN** 查询某 SKU 的可用库存
- **WHEN** 计算可售量
- **THEN** 仅统计 `available` 状态，排除 `in-transit` / `locked` / `defective`

### Requirement: 退货冲回汇总
Negative records (returns / chargebacks) SHALL be aggregated consistently with `saleNum`; filtering of reversal records MUST be explicit and aligned with the temu-semi 半托管账务逻辑.

#### Scenario: 销售冲回
- **GIVEN** 一笔退货冲回记录
- **WHEN** 汇总 `saleNum`
- **THEN** 按既定规则明确纳入或过滤，逻辑与 temu-semi 半托账务明细一致

### Requirement: 幂等接收
Inbound platform pushes SHALL be deduplicated by an idempotency key to tolerate duplicate delivery.

#### Scenario: 重复推送
- **GIVEN** 平台重复推送同一订单
- **WHEN** 接收处理
- **THEN** 基于幂等键去重，仅落地一次
