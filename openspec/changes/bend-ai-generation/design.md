# Design: B 端 AI 生成应用

## 在架构中的位置
Liganex 现已明确为"两层产品面"：
- **基础设施层**：MCP server、hub、studio（客户端）、skill、AI 自动化/可观测/沙箱。
- **应用层（本变更）**：面向 B 端客户的 AI 生成应用，是平台的营收面，同时也是基础设施层的消费方。

数据流：B 端客户 → studio 工作台 UI → gen 后端（模型供应商抽象 + 异步任务 + 配额）→ 供应商（Volcengine 等）；
可选回环：gen 后端 → liganex-mcp → ERP 数据，作为生成上下文。

## 模型供应商抽象层
定义统一接口，四类生成各对应一个方法：
```
generateText(prompt, template, opts)   -> TextResult
generateImage(prompt, opts)            -> ImageResult
editImage(imageRef, prompt, mask, opts)-> ImageResult
generateVideo(imageRef, motion, opts)  -> VideoResult
```
- 各供应商实现适配器；配置化静态路由（首期仅 Volcengine）。
- 首期 Volcengine 映射：文本→方舟/豆包，图像→Seedream，视频→即梦。
- 统一返回标准化结构（资产 URL、供应商、参数快照、错误码），便于前端与计费统一处理。

## 异步任务框架
生图/生视频为长任务（秒级~分钟级）。设计：
- 提交即落库并返回 `taskId`，状态机 `pending → running → succeeded/failed`。
- 执行层可用线程池/消息队列（首期单机内存队列即可，后续接 sandbox 隔离执行）。
- 支持轮询与（可选）Webhook 回调；结果资产元数据持久化，可回溯审计。

## 用量与配额（成本控制）
- 客户维度配额表：`customer_id, quota_type, monthly_limit, used`。
- 生成前预检配额，超额直接拒绝且**不调用供应商**（关键：避免成本失控）。
- 仅做用量统计与上限，不做完整计费账单（Non-goals）。

## 仓库归属提议（待确认）
- **生成后端** 建议独立为业务仓库 `liganex-gen`（模型抽象、异步任务、配额、资产存储），符合"前端不单独建仓、业务模块独立仓"的约定。
- **B 端工作台 UI** 并入 `liganex-studio` 的 `frontend/`（studio 作为 B 端门面，承载生成工作台 + 客户/配额管理）。
- 备选：将生成能力整体并入 `liganex-studio`（前后端同仓）。若生成后端较重、需独立伸缩，则首选分离方案。

## 与 ADR-0002 的边界澄清
- ADR-0002 的轻量授权（单一 API Key + 工具白名单 + 审计）**仅覆盖基础设施层**。
- 本 B 端应用面面向外部付费客户，需**客户级 API Key + 用量/配额控制**；这正是 ADR-0002 声明的"接入多租户/真实客户数据须补齐"的触发条件。
- 由新建 `docs/adr/ADR-0003-bend-auth-quota.md` 正式记录该边界（见 tasks）。

## 与 platform-extensions 的协同
- **可观测**：生成调用、供应商延迟、配额命中率应纳入 observability 规范（复用审计日志）。
- **沙箱**：高风险/第三方脚本类生成逻辑未来可放入 sandbox 隔离执行。
- **AI 自动化**：生成的素材可接入自动化工作流（如定时批量生成 Listing）。
