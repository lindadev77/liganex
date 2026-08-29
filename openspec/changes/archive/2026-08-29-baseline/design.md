## Context

项目处于实验性开源初期。架构、技术栈、协议版本、数据模型与安全取舍已在 README 与 `docs/adr/` 中明确。本变更将这些决策固化为可验证的 OpenSpec 源规范，作为后续所有 feature change 的基线——任何新需求都应在基线之上增量演进，而非重写契约。

## Goals / Non-Goals

**Goals:**
- 建立 `project-architecture`、`mcp-server`、`cross-border-erp-data`、`security` 四个 capability 的 baseline spec
- 使"为什么选 A 不选 B"的权衡可通过 ADR + spec 双重追溯

**Non-Goals:**
- 不在此变更中实现任何代码（baseline 是文档化，不是开发）
- 不引入任何新决策（仅固化已有决策）

## Decisions

- 采用 "baseline change + archive" 的方式生成 main specs，而非手写 `openspec/specs/`，以保证文件格式完全符合 OpenSpec 校验
- 安全范围严格对齐 ADR-0002（轻量授权），避免 OAuth 等重授权机制的范围蔓延
- 数据模型强调五类已知风险（时区 / 汇率 / 库存口径 / 退货冲回 / 幂等）——这些来自真实跨境 ERP 经验，是本项目的差异化亮点

## Risks / Trade-offs

- baseline spec 描述的是目标契约，部分能力（如 MCP server 实现）尚未落地；`openspec validate` 仅检查格式与一致性，不保证实现存在
- 若后续协议规范再次修订，需通过新的 change 更新 `mcp-server` capability 而非就地修改
