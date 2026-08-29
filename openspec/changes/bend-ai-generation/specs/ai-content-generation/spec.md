# Spec: ai-content-generation

面向 B 端客户的 AI 内容生成能力，覆盖文生文/文生图/图生图/图生视频，并统一经模型供应商抽象层接入。

## ADDED Requirements

### Requirement: 模型供应商抽象层
系统 SHALL 提供统一的模型供应商抽象接口，屏蔽具体厂商差异，支持文生文、文生图、图生图、图生视频四类调用。

#### Scenario: 经抽象层路由生成请求
- **GIVEN** 已配置至少一家模型供应商（首期 Volcengine）
- **WHEN** 业务层发起任意一类生成请求
- **THEN** 请求经抽象层路由到对应供应商适配实现，并返回标准化结果（含资产 URL / 文本内容 / 错误码）

#### Scenario: 供应商可插拔
- **GIVEN** 抽象层已定义 `generateText/generateImage/editImage/generateVideo` 接口
- **WHEN** 新增一家供应商（如 OpenAI）
- **THEN** 仅需实现对应适配器并注册，无需改动业务层代码

### Requirement: 文生文（text-to-text）
系统 SHALL 支持以文本提示词生成文本，首期场景含商品文案、多语言翻译/Listing 优化、客服话术。

#### Scenario: 生成商品文案
- **GIVEN** 用户提供商品标题与卖点关键词
- **WHEN** 调用文生文并选择"商品文案"模板
- **THEN** 返回符合跨境平台调性的标题/五点描述/搜索词

#### Scenario: 多语言翻译
- **GIVEN** 一段中文商品描述与目标语言（如 EN/DE/JP）
- **WHEN** 调用文生文翻译
- **THEN** 返回保留电商术语准确性的译文

### Requirement: 文生图（text-to-image）
系统 SHALL 支持以文本提示词生成图像，首期场景含商品主图、场景图、营销图。

#### Scenario: 生成商品主图
- **GIVEN** 用户提供商品描述与风格约束
- **WHEN** 调用文生图
- **THEN** 返回图像资产 URL 及生成参数快照

### Requirement: 图生图（image-to-image）
系统 SHALL 支持以图像+提示词进行图像编辑，首期场景含背景替换、风格迁移、局部重绘（inpainting）。

#### Scenario: 商品背景替换
- **GIVEN** 一张商品白底图与目标场景描述
- **WHEN** 调用图生图并指定"背景替换"
- **THEN** 返回替换背景后的图像资产

### Requirement: 图生视频（image-to-video）
系统 SHALL 支持以图像生成短视频，首期场景含商品展示视频、营销短片。

#### Scenario: 生成商品展示视频
- **GIVEN** 一张商品主图与运动/时长参数
- **WHEN** 调用图生视频
- **THEN** 返回视频资产 URL 及生成任务标识

### Requirement: 异步任务与状态
系统 SHALL 将生图/生视频等长任务建模为异步任务，支持提交、状态查询、回调通知与结果持久化。

#### Scenario: 提交长任务并轮询
- **GIVEN** 用户提交一个图生视频任务
- **WHEN** 任务进入队列
- **THEN** 立即返回 taskId，用户可凭 taskId 查询 pending/running/succeeded/failed 状态并获取结果

#### Scenario: 任务结果持久化
- **GIVEN** 任务执行成功
- **THEN** 资产元数据（URL、供应商、参数、耗时、客户归属）写入存储，可回溯

### Requirement: 用量与配额控制
系统 SHALL 按 B 端客户维度记录生成用量并施配额上限，防止成本失控。

#### Scenario: 配额内正常生成
- **GIVEN** 客户当月用量未达配额
- **WHEN** 发起生成请求
- **THEN** 正常执行并累加用量计数

#### Scenario: 超额拒绝
- **GIVEN** 客户当月用量已达配额上限
- **WHEN** 发起生成请求
- **THEN** 拒绝并返回明确配额超限错误，不调用供应商（不产生成本）

### Requirement: ERP 数据作为生成上下文（可选）
系统 SHOULD 支持在生成前经 liganex-mcp 拉取商品/订单数据，使产出贴合真实业务（grounded generation）。

#### Scenario: 以真实商品属性生成文案
- **GIVEN** 用户选定某 SKU 并要求"据此生成 Listing"
- **WHEN** 系统经 liganex-mcp 读取该 SKU 的属性/价格/库存
- **THEN** 生成的文案包含真实卖点且数值与 ERP 一致，不臆造

### Requirement: B 端流式对话工作台
系统 SHALL 为 B 端客户提供流式（逐 token）对话工作台，作为 AI 生成能力与 ERP 数据查询的统一交互入口。

#### Scenario: 流式输出对话回复
- **GIVEN** B 端客户在工作台发起一条对话消息
- **WHEN** 后端开始生成回复
- **THEN** 响应以 SSE/流式方式逐 token 返回，前端实时渲染，且支持中途取消

#### Scenario: 多轮上下文理解
- **GIVEN** 用户已进行多轮对话并上传过参考图
- **WHEN** 用户追问"把刚才那张主图换成红色背景"
- **THEN** 系统基于会话上下文理解指代，无需重复上传或描述（会话状态由应用侧维护）

#### Scenario: 意图路由到生成或 ERP 查询
- **GIVEN** 用户输入"给这个 SKU 写个德语 Listing"或"美国仓还有多少库存"
- **WHEN** 对话引擎解析意图
- **THEN** 前者路由到文生文生成能力，后者经 liganex-mcp 查询 ERP 并返回结构化结果

#### Scenario: 对话内联展示生成结果
- **GIVEN** 用户在对话中触发一次文生图
- **WHEN** 生成完成
- **THEN** 图像资产直接内联展示在对话流中，可一键插入草稿或下载
