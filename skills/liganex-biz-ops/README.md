# Liganex 业务运营 Skill（开放平台对接包）

本压缩包是 liganex 开放平台提供给 **agent 终端**（Qoder、workbuddy 等支持加载
SKILL.md 的客户端）的能力包：装上并把你在开放平台申请的应用凭证交给 agent，
就可以在对话里直接查订单、发货、维护商品、调整库存。

## 包内容

```
liganex-biz-ops/
├── SKILL.md                  # agent 读的说明书（调用流程 + 工具表 + 错误处置）
├── skill.json                # 名称/版本清单
├── README.md                 # 本文件（给安装者看）
└── scripts/
    └── liganex_mcp.py        # HMAC 签名客户端，仅依赖 python3 标准库
```

## 安装（约 1 分钟）

1. 解压，把 `liganex-biz-ops/` 整个目录放进你的 agent 终端的 skill 目录。
   - Qoder：项目下 `.agents/skills/`，或插件的 `skills/` 目录
   - 其他终端：任何会加载 `SKILL.md` 的技能目录
2. 在 liganex 开放平台页面注册/登录，进入「我的应用」：
   - 创建应用 → 弹窗里**一次性展示** App ID 与 App Secret，立即保存
   - 「管理权限」勾选该应用需要的接口权限（页面只列出已开放的权限）
3. 告诉 agent：「用 liganex-biz-ops skill，appId 是 …，密钥是 …，服务地址是 …」。
   agent 会执行：

   ```bash
   python3 <skill目录>/scripts/liganex_mcp.py setup \
     --app-id <App ID> --app-secret <App Secret> \
     --url <服务部署地址>        # 例如 https://liganex.your-company.com，本地默认 http://127.0.0.1:8081
   ```

   凭证保存在 `~/.liganex/credentials`（chmod 600，仅本人可读）。
4. 之后直接对话即可，例如「查一下美国区已支付的订单」「给这笔订单发货」。

## 安全须知

- App Secret 只展示一次，丢失请在开放平台重置后重新 setup。
- 密钥等同账号凭证：不要把 `~/.liganex/credentials` 提交进代码库或发给他人。
- 每次调用都会在服务端做签名校验、权限（scope）校验、配额与审计；
  应用被禁用或权限未勾选时调用会被明确拒绝。

## 常见问题

| 现象 | 处理 |
|---|---|
| 无法连接 | 确认服务已部署且 `--url` 正确（含端口/协议） |
| 签名校验失败 | 密钥错误或已轮换：开放平台重置密钥后重新 setup |
| 应用未授权该权限 | 到应用「管理权限」补勾对应权限 |
| 需要哪些工具/权限 | `python3 scripts/liganex_mcp.py tools` |
