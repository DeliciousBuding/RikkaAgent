# MASTER.md — RikkaAgent 重构进度追踪

> 最后更新：2026-06-23
> 追踪模式：LOCAL_ONLY

## 任务定义

**目标**：1:1 复刻 RikkaHub UI/前端层，适配 SSH 命令执行场景，UI 完全一致。

**范围**：RikkaAgent 全仓库重构（UI + 数据模型 + 存储 + SSH 引擎 + 安全加固）

**约束**：
- ADB (Mumu 模拟器) + Sonnet 模型截图验收
- 仓库必须适合对外展示（安全/隐私/产品口径）
- 每阶段结束有可截图验收的 UI 产出

## 文档索引

| 类别 | 文档 | 状态 |
|------|------|------|
| 分析 | [项目概览](../analysis/project-overview.md) | ✅ |
| 分析 | [综合技术分析报告](../analysis/analysis-report.md) | ✅ |
| 规划 | [任务分解](../plan/task-breakdown.md) | ✅ |
| 规划 | [依赖图](../plan/dependency-graph.md) | ✅ (见 task-breakdown.md) |
| 规划 | [里程碑](../plan/milestones.md) | ✅ (见 task-breakdown.md) |

## 阶段总览

| 阶段 | 状态 | 任务数 | 说明 |
|------|------|--------|------|
| Phase 0: 同步与基线 | ⏳ 待开始 | - | 同步 RikkaHub fork 到上游 2.3.2 |
| Phase 1: 数据层重构 | ⏳ 待开始 | - | 消息模型升级 + 存储修复 |
| Phase 2: UI 基础设施 | ⏳ 待开始 | - | 主题系统 + 组件库基础对齐 |
| Phase 3: 核心 UI 复刻 | ⏳ 待开始 | - | ChatScreen/ChatInput/侧边栏 1:1 复刻 |
| Phase 4: SSH 适配 | ⏳ 待开始 | - | SSH exec 流式输出适配 |
| Phase 5: 安全与展示 | ⏳ 待开始 | - | 安全加固 + 对外展示准备 |

## 当前状态

**活跃阶段**：Phase 3 规划中
**下一步**：等待任务分解完成，确认规划后开始执行

## 执行遥测

| 任务 | 实际工作量 | S.U.P.E.R 评分 | 备注 |
|------|-----------|---------------|------|
| Phase 1 分析 | - | - | 15 agents 并行，~12min |
