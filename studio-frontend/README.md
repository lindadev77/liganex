# studio-frontend

Liganex Studio 的 B 端前端，作为 `liganex` 主仓顶层独立工程（原 `liganex-studio` 临时仓已并入本仓）。

## 技术栈

React 19.2 · TypeScript 5.9 · Vite 8.2 · Ant Design 6.6 · react-router-dom 7 · axios

## 与后端联调

开发期用 Vite 反代把 `/api` 转到后端（`vite.config.ts`：`/api → http://127.0.0.1:8081`，规避跨域）；
生产环境用 nginx 反代 `/api` 到后端（拓扑与 dev 一致，见 ADR-0007）。因此本地需先启动后端（默认 8081）。

## 启动

```bash
cd studio-frontend
npm install
npm run dev        # 开发服务器 http://127.0.0.1:5173
```

## 构建

```bash
npm run build      # 产物输出 dist/（已被 .gitignore 忽略）
```

## 目录

```
src/
├── api/          # axios 实例（code===0 解包、401 自动 refresh）+ 各业务接口
├── context/      # AuthContext（localStorage 存 token + user）
├── layouts/      # MainLayout 主框架
├── pages/        # LoginPage / RegisterPage / OpenAppPage（我的应用 + 创建 + 权限）
└── router.tsx    # 路由
```

开放平台「我的应用」页面：注册用户可自行创建应用（一次性展示 appId/secret）、勾选接口权限（如 `order:read`），
对应后端 `/api/v1/open-app/**` 与 MCP `/mcp/v1` 签名鉴权链路。
