# 本地开发环境搭建

本文件说明如何在本机拉起 Liganex 的基础设施（Postgres + pgvector / Redis 8 / RocketMQ 5）。
选型依据见 [ADR-0004 数据架构与存储扩展策略](adr/ADR-0004-data-storage-architecture.md)，
密钥处理约定见 [ADR-0007 配置与密钥管理](adr/ADR-0007-config-and-secrets-management.md)。

## 1. 前置条件：容器运行时（OrbStack）

容器运行时统一使用 **OrbStack**（替代 Docker Desktop，Apple Silicon 上更轻量、启动更快）。

```bash
brew install --cask orbstack
```

- 首次使用需在 OrbStack 窗口中完成初始化（安装 helper 组件，需要输入一次系统密码）。
- OrbStack 自带 `docker` / `docker-compose` CLI，安装并首次启动后生成在 `~/.orbstack/bin`，需要加入 PATH：

```bash
# ~/.zshrc
export PATH="$HOME/.orbstack/bin:$PATH"
```

验证：

```bash
orb status          # Running
docker version --format '{{.Server.Version}}'
docker compose version
```

> 若 `orb start` 报 `install rosetta: ... couldn't be opened`，是执行环境的 `TMPDIR` 被重定向所致，
> 用 `env TMPDIR=/private/tmp orb start` 启动即可（Rosetta 本身一般已随系统安装）。

## 2. 一键启动

```bash
cd infra/local-dev
cp .env.example .env      # .env 已被 .gitignore 忽略，绝不入库
docker compose up -d
docker compose ps         # 四个服务全部 healthy
```

可选组件（按需）：

```bash
docker compose --profile grpc  up -d   # RocketMQ Proxy，供 gRPC 客户端使用
docker compose --profile tools up -d   # RocketMQ Dashboard，访问 http://localhost:8180
```

## 3. 服务清单与连接信息

| 服务 | 镜像 | 宿主端口 | 账号 / 说明 |
|---|---|---|---|
| Postgres + pgvector | `pgvector/pgvector:pg18` | 5432 | `liganex` / `liganex_dev`，库 `liganex` |
| Redis | `redis:8-alpine` | 6379 | 无口令；AOF 开启，maxmemory 512MB |
| RocketMQ NameServer | `apache/rocketmq:5.3.3` | 9876 | Java remoting 客户端连此地址 |
| RocketMQ Broker | `apache/rocketmq:5.3.3` | 10911 / 10912 / 10909 | `brokerIP1=127.0.0.1`，便于宿主机直连 |

已验证版本：**PostgreSQL 18.6 + pgvector 0.8.6**、**Redis 8.10.1**、**RocketMQ 5.3.3**、Docker Engine 29.4.0。

## 4. 验证

```bash
# Postgres 与向量检索
docker exec liganex-postgres psql -U liganex -d liganex -c "SELECT version();"
docker exec liganex-postgres psql -U liganex -d liganex -c "CREATE EXTENSION IF NOT EXISTS vector;"
docker exec liganex-postgres psql -U liganex -d liganex -c \
  "SELECT '[1,2,3]'::vector <-> '[4,5,6]'::vector AS l2_distance;"

# Redis
docker exec liganex-redis redis-cli INFO server | grep redis_version
docker exec liganex-redis redis-cli PING

# RocketMQ：集群与端到端收发
docker exec liganex-rocketmq-namesrv sh -c "sh mqadmin clusterList -n localhost:9876"
docker exec -e NAMESRV_ADDR=liganex-rocketmq-namesrv:9876 liganex-rocketmq-broker sh -c \
  'sh /home/rocketmq/rocketmq-5.3.3/bin/tools.sh org.apache.rocketmq.example.quickstart.Producer'
docker exec -e NAMESRV_ADDR=liganex-rocketmq-namesrv:9876 liganex-rocketmq-broker sh -c \
  'sh /home/rocketmq/rocketmq-5.3.3/bin/tools.sh org.apache.rocketmq.example.quickstart.Consumer'
```

端口连通性（宿主视角）：

```bash
for p in 5432 6379 9876 10911; do nc -z -G 3 127.0.0.1 $p && echo "$p OPEN"; done
```

## 5. 常用运维

```bash
docker compose logs -f <service>      # 跟踪日志
docker compose stop | start | restart # 停止 / 启动（保留数据）
docker compose down                   # 销毁容器（保留数据卷与 data/ 目录）
rm -rf infra/local-dev/data           # 彻底清空 RocketMQ 数据（会丢消息）
docker volume rm liganex-local-dev_pgdata  # 清空 Postgres 数据，下次启动重新初始化
```

## 6. 踩坑记录

以下问题都实际发生过，改动环境配置时请留意。

### 6.1 PG18+ 镜像必须挂载 `/var/lib/postgresql`

PG18 的官方镜像把数据放到版本化子目录（`<major>/data`），挂载 `/var/lib/postgresql/data`
会触发 "there appears to be PostgreSQL data in ..." 并反复重启。正确写法：

```yaml
volumes:
  - pgdata:/var/lib/postgresql     # 而非 /var/lib/postgresql/data
```

### 6.2 RocketMQ 5.x 不再识别 4.x 的 `mapedFileSize*`

RocketMQ 5.x 已把 `mapedFileSizeCommitLog` / `mapedFileSizeConsumeQueue` 更名为 `mappedFileSize*`。
在 `broker.conf` 里沿用旧属性名会导致 Broker 启动失败，且日志里只看到
`ScheduleMessageService.configFilePath` 的 `NullPointerException`（真正的解析错误被 shutdown 流程掩盖）。
本项目的 `broker.conf` 不显式设置这两个值，使用镜像默认。

### 6.3 Broker 的 store 目录要挂宿主机目录，不要用命名卷

RocketMQ 容器以 `uid=3000(rocketmq)` 运行，而 OrbStack 新建的命名卷默认是 `root:root 755`，
Broker 无法写入 store 目录，表现同样是启动时崩溃。解决办法是挂载宿主机目录并放开权限：

```bash
mkdir -p data/namesrv/store data/broker/store && chmod -R 777 data
```

（`data/` 已被 `.gitignore` 忽略。）

### 6.4 `brokerIP1=127.0.0.1` 的取舍

设为 `127.0.0.1` 后，宿主机的 Java 客户端经 NameServer 拿到路由后可直接连 `127.0.0.1:10911`，
由 docker 端口映射转发进容器，本地单机场景最简单。

代价：从 NameServer 容器内部执行 `mqadmin clusterList` 时，它会按 `127.0.0.1:10911` 回连自己，
因此 Version / ACTIVATED 等运行时统计取不到（属预期现象，不影响消息收发）。
若改用 gRPC（proxy）客户端，需把 `brokerIP1` 换成容器可解析的地址，例如 OrbStack 的
`liganex-rocketmq-broker.orb.local`。

### 6.5 Docker Hub 直连不通时配置 registry mirror

若 `docker compose up -d` 报 `failed to resolve reference ... Bad Gateway`，
说明访问 `registry-1.docker.io` 被阻断。为 OrbStack 的 docker daemon 配置镜像源：

```json
// ~/.orbstack/config/docker.json
{
  "registry-mirrors": [
    "https://docker.1panel.live",
    "https://hub.rat.dev",
    "https://docker.m.daocloud.io"
  ]
}
```

然后 `orb restart docker`，用 `docker info --format '{{json .RegistryConfig.Mirrors}}'` 确认生效。

## 7. 后续

- Testcontainers 集成测试骨架（`local-dev-env` 2.3）：集成测试复用本套镜像，保证测试库与生产库同源（ADR-0005）
- 沙箱部署流水线（`local-dev-env` 2.4）：agent 改码 → 构建 → 临时沙箱 → 自动化测试 → 开 PR
