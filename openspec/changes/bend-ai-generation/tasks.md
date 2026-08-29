# Tasks: B 端 AI 生成应用

> 规划态任务，实现时按此拆分为独立 change。单条不超过 2 小时工作量。

## 设计/契约
- [ ] 定义模型供应商抽象接口 `generateText/generateImage/editImage/generateVideo` 与统一返回结构
- [ ] 确定 `liganex-gen` 与 `liganex-studio` 的仓库归属（采纳 design.md 提议或备选）
- [ ] 新建 `docs/adr/ADR-0003-bend-auth-quota.md`（B 端应用多租户/配额授权边界）

## 供应商接入
- [ ] 实现 Volcengine 适配器：方舟文本 / Seedream 图像 / 即梦视频
- [ ] 配置化静态路由，验证抽象层可插拔

## 生成能力 MVP（每类独立验证）
- [ ] 文生文 MVP：商品文案 + 多语言翻译
- [ ] 文生图 MVP：商品主图/场景图
- [ ] 图生图 MVP：背景替换 + 局部重绘
- [ ] 图生视频 MVP：商品展示视频

## 平台能力
- [ ] 异步任务框架：提交/状态机/轮询/结果持久化
- [ ] 用量与配额服务：客户级统计 + 超额拒绝（不调用供应商）
- [ ] ERP 数据上下文接入（可选）：经 liganex-mcp 读取 SKU 属性生成 grounded 文案

## 前端
- [ ] B 端生成工作台 UI（liganex-studio frontend）：四类生成入口、任务列表、配额展示

## 流式对话工作台
- [ ] 对话后端：消息接收、意图路由（生成 vs ERP 查询）、SSE 流式推送、会话上下文管理
- [ ] 前端流式对话 UI（liganex-studio）：实时渲染、上传参考图、中断/重试
- [ ] 与生成能力打通：对话中直接触发文生文/文生图并内联展示结果

## 验证
- [ ] `openspec validate bend-ai-generation` 通过
- [ ] 端到端冒烟：提交一次文生图 → 配额扣减 → 异步完成 → 资产可查看
