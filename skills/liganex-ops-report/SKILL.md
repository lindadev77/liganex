---
name: liganex-ops-report
description: 通过 liganex 开放平台 MCP 接口做运营分析——按地区/时间统计订单量与 GMV、商品目录盘点、分仓库存快照。只读场景：当用户要求出报表、看数据、统计、盘点、快照时使用。需要开放平台应用凭证（建议只勾三个 read 权限）。
---

# liganex 运营分析 — 只读报表（走 MCP）

本 skill 面向**分析与汇报**场景：基于订单/商品/库存的只读查询做统计与汇总，
**不改变任何业务数据**。写入类操作（建单、发货、改商品、调库存）属于
`liganex-biz-ops` 包的职责，本包不涉及。

签名与调用机制与所有 liganex skill 一致，封装在 `scripts/liganex_mcp.py`。

## 第 0 步：确认凭证已配置

```bash
test -f ~/.liganex/credentials && echo OK || echo MISSING
```

- **存在** → 直接进入「分析流程」。
- **不存在** → 引导用户在开放平台创建应用，**只勾选三个只读权限**：
  `order:read` / `product:read` / `inventory:read`（最小权限，分析够用），
  然后：

```bash
python3 <skill目录>/scripts/liganex_mcp.py setup \
  --app-id <appId> --app-secret <密钥> --url <服务地址>
```

## 本包使用的工具（只读）

| 工具 | 权限 | 用途 | 关键参数 |
|---|---|---|---|
| `order_query` | order:read | 订单分页查询 | `region` `status` `from` `to`（ISO-8601）`page` `size` |
| `product_query` | product:read | 商品目录分页查询 | `keyword` `region` `page` `size` |
| `inventory_query` | inventory:read | 分仓库存查询 | `sku` `region` `warehouse` |

调用形式：

```bash
python3 <skill目录>/scripts/liganex_mcp.py call <工具名> --args '{...}'
```

## 分析流程（重要约定）

服务端只提供过滤与分页，**统计汇总由你在本地完成**：

1. **明确口径**：先和用户确认统计维度（地区/状态/时间范围）与指标（订单量、
   GMV=Σamount、SKU 数、库存量）。时间用 ISO-8601，注意用户说的「本月」要转成
   具体 `from`/`to`。
2. **分页拉全量**：`size` 最大建议 100，`page` 从 1 递增，直到取回条数小于
   `size` 或达到 `total`。数据量大时先取样（前几页）并告知用户是抽样结果。
3. **本地聚合**：用 python3 做分组求和/计数，输出表格（地区 × 指标），金额保留
   两位小数并注明币种；跨币种时分开统计，不要擅自换算。
4. **呈现结论**：先给结论摘要（总量、TOP 地区/状态），用户要明细再展开。

### 常用报表套路

- **订单量 & GMV（按地区）**：按时间窗循环 `order_query`，按 `region` 分组，
  统计订单数与 `amount` 求和（按 `currency` 分列）。
- **履约漏斗**：同一时间窗分别按 `status` 查
  （PENDING → PAID → SHIPPED → DELIVERED，另有 CANCELLED），对比各环节单量。
- **商品目录盘点**：`product_query` 翻页拉全，统计 SKU 总数、按地区分布、
  均价区间。
- **库存快照**：`inventory_query` 不带过滤拉全，按 `warehouse`/`sku` 汇总
  `availableQty`，标出低库存（低于用户给定阈值）项。

## 常见错误与处置

| 错误信息 | 原因 | 处置 |
|---|---|---|
| 应用未授权该权限 | 应用没勾对应 read 权限 | 让用户补勾；本包只需要三个 read |
| 签名无效 | 密钥错误或已轮换 | 开放平台重置密钥后重新 `setup` |
| 无法连接 | 服务未启动或 `--url` 不对 | 与服务方确认部署地址后重新 `setup` |

## 边界

- **严禁**调用 `order_write` / `order_update` / `order_ship` / `product_write` /
  `inventory_adjust`——即便用户要求，也应提示改用 `liganex-biz-ops` 包。
- 数据有分页与配额限制，超大时间范围建议拆窗分批拉取。
