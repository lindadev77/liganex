# liganex-studio-backend

Liganex Studio 的后端模块，作为 `liganex` 主仓 `server/` 多模块之一（原 `liganex-studio` 临时仓已并入本仓）。

架构决策见主仓 [`docs/adr/`](../../docs/adr/)：
ADR-0004（存储与分区）、ADR-0005（Flyway 迁移）、ADR-0006（模块化单体）、
ADR-0007（配置与密钥管理）、ADR-0008（前端选型）、ADR-0009（服务拆分边界与订单归属）。

## 技术栈

Java 25 LTS · Spring Boot 4.1.x · Maven · PostgreSQL 18（+pgvector）· Redis 8 · Flyway · MyBatis-Plus

## 三套独立鉴权（路径前缀隔离，见 ADR-0002 / ADR-0003）

| 体系 | 路径前缀 | 凭证 |
|---|---|---|
| 用户会话 JWT | `/api/v1/**` | Bearer JWT（面向人，B 端前端） |
| 应用凭证 HMAC 签名 | `/mcp/**` | appId + HMAC-SHA256 签名 + 时间戳窗口 + nonce 防重放（面向应用/skill） |
| 服务间 API Key | `/internal/v1/**` | `X-Internal-Api-Key`（MCP → studio 内部调用） |

## 前置：启动本地基础设施

```bash
cd ../../infra/local-dev        # 本仓 infra/local-dev
cp .env.example .env            # .env 已被 .gitignore 忽略，绝不入库
docker compose up -d
```

## 启动后端

密钥一律经环境变量注入，**不写入任何配置文件**（ADR-0007）：

```bash
cd server/liganex-studio-backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export LIGANEX_JWT_SECRET="$(openssl rand -base64 48)"   # 必填，HS256 至少 32 字节
export LIGANEX_SERVER_PORT=8081                          # 与前端 vite 反代目标一致
mvn spring-boot:run
```

> 也可从聚合根构建运行：`cd server && mvn -pl liganex-studio-backend spring-boot:run`。
> 未注入 `LIGANEX_JWT_SECRET` 时应用会启动失败（fail-fast），不会带着空密钥运行。

## 验证

```bash
# 注册
curl -X POST localhost:8081/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"linda@liganex.dev","password":"Liganex@2026","displayName":"Linda"}'

# 登录
curl -X POST localhost:8081/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"linda@liganex.dev","password":"Liganex@2026"}'

# 订单查询（带 JWT）
curl "localhost:8081/api/v1/orders?region=US&status=PAID" -H "Authorization: Bearer <accessToken>"
```

> curl 若走代理，访问 localhost 需加 `--noproxy '*'`。
