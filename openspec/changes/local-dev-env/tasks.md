## 1. 规划登记（本变更）
- [x] 1.1 登记 `local-development` 规划 capability（选型 + 开发闭环约定）
- [x] 1.2 明确与 `mcp-server` / `ai-content-generation` / `agent-dev-loop` 的协同

## 2. 后续实现（待启动，各自独立 change）
- [x] 2.1 起草 docker-compose（OrbStack 编排 PG+pgvector / Redis 8 / RocketMQ 5）— infra/local-dev/docker-compose.yml，四服务已 healthy 并端到端验证
- [x] 2.2 编写 docs/dev-setup.md（本地起环境步骤 + 踩坑记录）
- [ ] 2.3 Testcontainers 集成测试骨架
- [ ] 2.4 沙箱部署流水线（构建镜像 → 临时命名空间 → 种子数据 → 自动化测试）
