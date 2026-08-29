# Liganex 运营分析 Skill（只读报表包）

本压缩包是 liganex 开放平台提供给 **agent 终端**（Qoder、workbuddy 等）的
**运营分析**能力包：装上并配置应用凭证后，可以在对话里直接出报表——
按地区/时间统计订单量与 GMV、商品目录盘点、分仓库存快照。

> 与 `liganex-biz-ops`（运营操作包）的区别：本包**只读**，只需要三个 read 权限，
> 适合给分析/汇报岗位使用；建单、发货、改商品、调库存请用 `liganex-biz-ops` 包。

## 包内容

```
liganex-ops-report/
├── SKILL.md                  # agent 读的说明书（分析流程 + 报表套路 + 边界）
├── skill.json                # 名称/版本/所需权限清单
├── README.md                 # 本文件（给安装者看）
└── scripts/
    └── liganex_mcp.py        # HMAC 签名客户端，仅依赖 python3 标准库
```

## 安装（约 1 分钟）

1. 解压，把 `liganex-ops-report/` 整个目录放进你的 agent 终端的 skill 目录。
2. 在 liganex 开放平台「我的应用」创建应用，保存一次性展示的 App ID / App Secret，
   并**只勾选**：`order:read` / `product:read` / `inventory:read`。
3. 告诉 agent：「用 liganex-ops-report skill，appId 是 …，密钥是 …，服务地址是 …」。
   agent 会执行：

   ```bash
   python3 <skill目录>/scripts/liganex_mcp.py setup \
     --app-id <App ID> --app-secret <App Secret> --url <服务部署地址>
   ```

4. 之后直接对话即可，例如「统计本月各地区的订单量和 GMV」「出一份分仓库存快照」。

## 安全须知

- App Secret 只展示一次，丢失请在开放平台重置后重新 setup。
- 本包对应的应用按最小权限原则只给读权限；即便有人诱导，也无法经此包写入数据。
- 每次调用服务端都会做签名校验、权限（scope）校验、配额与审计。
