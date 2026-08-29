# ADR-0008: 前端技术选型 — React 19 + Ant Design 6（而非 Vue 3）

- **状态**：已采纳（2026-08-29）
- **决策人**：Linda
- **关联**：ADR-0001（项目定位）、config 技术选型原则（LTS 内最新稳定版）、project-architecture（单一前端入口）、bend-ai-generation（AI 创作与流式对话）

## 背景

前后端代码框架搭建前需定前端技术栈，候选为 Vue 3 与 React 19。

项目真实特征：

- 开源求职项目，主线定位为**全栈**（前后端都要讲），且为**长期迭代**项目
- 前端形态：B 端多菜单业务系统，单一前端入口（`liganex-studio/frontend`），菜单含工作台 / AI 创作 / 订单 / 商品 / 库存 / 数据看板 / 账户配额
- **AI 创作模块含流式对话**，是本项目区别于普通 ERP 的核心亮点
- 开发者为后端背景（Java / Spring Boot），前端投入需与后端、MCP、AI 链路权衡

2026-08 生态现状（本次选型的客观依据）：

| 维度 | React 侧 | Vue 侧 |
|---|---|---|
| 核心版本 | React 19.2 稳定 | Vue 3.5.41 稳定；3.6 仍为 RC（Vapor Mode 秋季 GA） |
| 组件库 | Ant Design 6（97.5K★，周下载 187 万，99.4% TS 覆盖） | Element Plus 2.14.5（27.7K★，周下载 52.8 万） |
| 中后台方案 | Ant Design Pro v6（React 19 + antd 6 + ProComponents v3） | vue-vben-admin 等社区模板 |
| **AI 对话 UI** | **Ant Design X（蚂蚁官方，RICH 范式：气泡对话 / 思维链 / 流式输入）** | Element-Plus-X（社区项目，移植 Ant Design X 理念） |
| 配套 | React Router、TanStack Query、Zustand | Vue Router 5、Pinia 4 |

## 决策

前端采用 **React 19 + TypeScript + Vite**；组件层采用 **Ant Design 6 + ProComponents v3 + Ant Design X**。

**工程层自建骨架，不直接套用 Ant Design Pro 全家桶**：

| 层 | 选型 | 说明 |
|---|---|---|
| 构建 | Vite | 不使用 Umi Max / utoopack，保持工程栈通用可迁移 |
| 路由 | React Router + 自建菜单与权限路由 | 菜单结构对应工作台 / AI 创作 / 订单 / 商品 / 库存 / 看板 / 配额 |
| 服务端状态 | TanStack Query | 缓存、去重、失效管理 |
| 客户端状态 | Zustand | 轻量，避免过度设计 |
| 样式 | antd 6 CSS 变量模式（cssVar）+ CSS Modules | 不引入 Tailwind，避免与 antd 类名冲突 |
| 国际化 | i18next | 跨境多地区为业务固有诉求 |
| 图表 | ECharts | 数据看板 |
| 测试 | Vitest + Playwright | 长期项目需回归保障 |
| 规范 / 包管理 | Biome / pnpm | 与 Node 22 LTS 配套（Vite 要求 Node 20.19+ 或 22.12+） |

流式对话走 SSE（EventSource 或 fetch + ReadableStream），与后端流式输出对接（bend-ai-generation）。

## 理由

- **AI 交互是决定性因素**：本项目最亮的点在 AI 创作与流式对话，Ant Design X 是官方维护、与 antd 6 同源的方案，可直接获得思维链渲染与流式输入交互；Vue 侧的 Element-Plus-X 为社区移植，成熟度与长期维护弱一档。核心亮点不应押在社区项目上。
- **契合全栈定位**：React 在大厂与 AI 产品公司的 JD 覆盖率更高；「React 19 + antd 6 + AI 原生交互」比「Vue + Element Plus」更能支撑本项目要讲的 Agent 应用故事。
- **长期项目可摊销学习成本**：JSX + Hooks 的闭包与依赖数组心智成本是一次性的，而生态与可讲性收益是持续的；已确认为长期迭代项目，该权衡成立。
- **工程层自建、组件层复用**：Ant Design Pro 的 Umi 全家桶黑盒感强，面试时难讲清底层；自建 Vite 骨架能说清每一层配置，而组件层直接用 ProComponents / Ant Design X 避免重复造轮子。
- **符合既定选型原则**：React 19.2 属稳定线，未采用任何 RC / 预发布版本（Vue 3.6 Vapor Mode 同理不采用）。

## 边界与替代方案

- **不采用 Next.js**：B 端后台无 SSR / SEO 诉求，前后端分离 SPA 即可，避免 App Router 带来的额外心智负担。
- **不套用 Ant Design Pro 模板**：仅复用其 ProComponents 组件层，不引入 Umi Max 全家桶。
- **Vue 3 仍是合理备选**，出现以下任一情况应重新评估（以独立 OpenSpec change 提出）：
  - 目标公司 / 团队技术栈以 Vue 为主
  - 需要在 2–4 周内产出可演示版本，前端投入必须最小化
  - 项目定位从「全栈」收缩为「后端 / Agent 后端」

## 后果

- 需承担 React Hooks 的学习与调试成本（闭包陷阱、依赖数组、重渲染），长期项目视野下可控。
- AI 创作模块可基于 Ant Design X 直接实现思维链与流式渲染，显著降低自研成本，加速出演示效果。
- 简历可形成完整叙事：后端 Spring Boot 3 + Java 21，前端 React 19 + antd 6 的 AI 原生中后台，且选型有明确理由、有对比、有触发重评估的条件——体现的是取舍判断而非跟风。
