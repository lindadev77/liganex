---
name: liganex-biz-ops
description: 通过 liganex 开放平台 MCP 接口对话式读写业务数据（订单/商品/库存）。当用户提到查询订单、发货、更新订单状态、查商品、建商品、查库存、调整库存，或提到 liganex、MCP 业务数据时使用。需要先用开放平台应用凭证（appId + 密钥）完成一次 setup。
---

# liganex 业务运营 — 对话式业务数据访问（走 MCP）

本 skill 让 agent 通过 liganex 开放平台的 MCP 接口（JSON-RPC over HTTP + HMAC 应用签名）直接读写业务数据。所有签名逻辑封装在 `scripts/liganex_mcp.py`，你只需按下面的流程调用。

## 第 0 步：确认凭证已配置

每次会话第一次使用时检查：

```bash
test -f ~/.liganex/credentials && echo OK || echo MISSING
```

- **存在** → 直接进入「调用工具」。
- **不存在** → 引导用户获取凭证（密钥只在创建时展示一次）：
  1. 打开开放平台页面（地址由服务方提供；本地开发为 `http://localhost:5173`，登录后进入「我的应用」）；
  2. 创建应用，记下 **appId** 与弹窗中**一次性展示的密钥**；
  3. 在应用详情里勾选该应用需要的权限（见下方工具-权限表，只展示已开放的权限）；
  4. 把两个值与服务地址给你后，运行（`--url` 为开放平台后端部署地址，本地默认 `http://127.0.0.1:8081`）：

```bash
python3 <skill目录>/scripts/liganex_mcp.py setup \
  --app-id <appId> --app-secret <密钥> --url <服务地址>
```

凭证写入 `~/.liganex/credentials`（chmod 600）。用户不想落盘也可改用环境变量 `LIGANEX_APP_ID` / `LIGANEX_APP_SECRET`（可选 `LIGANEX_MCP_URL`，默认 `http://127.0.0.1:8081`）。

**密钥只展示一次**：如果用户弄丢了密钥，让他在开放平台重置密钥后重新 setup。

首次配置后运行一次验证：

```bash
python3 <skill目录>/scripts/liganex_mcp.py check
```

`check` 会用 `order_query` 走完整签名链路；若报「未授权」说明签名没问题但该应用没勾 `order:read`，让用户回开放平台补勾权限。

## 调用工具

统一形式（`<skill目录>` = 本 SKILL.md 所在目录）：

```bash
python3 <skill目录>/scripts/liganex_mcp.py call <工具名> --args '{...JSON参数...}'
```

也可以用多个 `--arg k=v` 代替 `--args`。输出是解开 JSON-RPC 封装后的业务 JSON。

另有两个辅助命令：
- `tools` — 列出服务端当前全部工具及各自需要的权限；
- `check` — 验证凭证与权限链路。

## 工具清单（8 个）

| 工具 | 所需权限 | 用途 | 关键参数 |
|---|---|---|---|
| `order_query` | order:read | 查询订单（分页） | `region` `status` `from` `to`（ISO-8601）`page` `size` |
| `order_write` | order:write | 创建订单 | `region`(默认US) `status`(默认PENDING) `amount`(默认0) `currency`(默认USD) `buyerName`；返回 `orderNo` |
| `order_update` | order:write | 更新订单状态 | `orderNo` `status`（PAID/SHIPPED/DELIVERED/CANCELLED） |
| `order_ship` | order:write | 发货（登记运单并置为 SHIPPED） | `orderNo` `carrier` `trackingNo` |
| `product_query` | product:read | 查询商品目录（分页） | `keyword`（名称/SKU 模糊）`region` `page` `size` |
| `product_write` | product:write | 按 SKU 新建/更新商品 | **必填** `sku` `name`；可选 `region` `price` `currency` `stock` |
| `inventory_query` | inventory:read | 查询分仓库存 | `sku` `region` `warehouse` |
| `inventory_adjust` | inventory:write | 调整某仓可用库存 | `sku` `region` `warehouse` `delta`（正入库/负出库，目标库存记录必须已存在） |

## 调用示例

```bash
# 查美国区已支付订单
python3 scripts/liganex_mcp.py call order_query --args '{"region":"US","status":"PAID"}'

# 创建一笔欧洲订单并拿到订单号
python3 scripts/liganex_mcp.py call order_write --args '{"region":"EU","amount":88.8,"currency":"EUR","buyerName":"Alice"}'

# 发货（登记运单）
python3 scripts/liganex_mcp.py call order_ship --args '{"orderNo":"LNX-2026-08-29-XXXX","carrier":"DHL","trackingNo":"DHL123456"}'

# 新建/更新商品（按 SKU upsert）
python3 scripts/liganex_mcp.py call product_write --args '{"sku":"SKU-2001","name":"便携榨汁杯","region":"US","price":24.99}'

# LA 仓入库 50 件
python3 scripts/liganex_mcp.py call inventory_adjust --args '{"sku":"SKU-1001","warehouse":"LA-01","region":"US","delta":50}'
```

## 常见错误与处置

| 错误信息 | 原因 | 处置 |
|---|---|---|
| 签名无效 / signature | 密钥错误或已轮换 | 让用户在开放平台重置密钥，重新 `setup` |
| 应用未授权该权限 / scope | 应用没勾对应权限 | 让用户在应用详情页勾选（页面只列出已开放的权限） |
| 无法连接 | 服务未启动或 `--url` 不对 | 与服务方确认部署地址（含端口/协议）后重新 `setup` |
| 订单不存在 | orderNo 错误 | 先 `order_query` 确认订单号 |
| 未找到对应的分仓库存记录 | 该仓没有该 SKU 的库存行 | 先 `inventory_query` 看有哪些仓，库存行由后台初始化，adjust 不能凭空建仓 |
| 时间戳超出窗口 / 重放 | 本机时钟偏差或重复请求 | 校准系统时间；重试即可 |

## 约定

- **写操作前确认**：`order_write` / `order_update` / `order_ship` / `product_write` / `inventory_adjust` 都会改变业务数据，执行前向用户复述要写入的内容并确认。
- 分页查询默认每页 20 条；结果较多时先给摘要，用户要全量再展开。
- 所有金额用 `number`，币种默认 `USD`。
