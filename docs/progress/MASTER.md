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
| 规划 | 依赖图 | ✅ (见 task-breakdown.md) |
| 规划 | 里程碑 | ✅ (见 task-breakdown.md) |

## 阶段总览

| 阶段 | 状态 | 任务数 | 说明 |
|------|------|--------|------|
| Phase 0: 同步与基线 | ✅ 完成 | 1 | RikkaHub fork 同步到 v2.3.2 |
| Phase 1: 数据层重构 | ✅ 基本完成 | 4 | 1A ✅ 1B ✅ 1C ✅ 1D ✅ |
| Phase 2: UI 基础设施 | ✅ 完成 | 4 | 2A ✅ 2B ✅ 2C ✅ 2D ✅ |
| Phase 3: 核心 UI 复刻 | ✅ 基本完成 | 8 | 3A ✅ 3B ✅ 3C ✅ 3D ✅ 3E ✅ 3F ✅ 3G ✅ 3H ✅ |
| Phase 4: SSH 适配 | ✅ 基本完成 | 4 | 4A ✅ 4B ✅ 4C ✅ 4D ✅ |
| Phase 5: 安全与展示 | 🔄 进行中 | 4 | 5A ✅ 5B ✅ 5C ⏳ 5D ✅ |

## 当前状态

**活跃阶段**：多轮并行深度完善中
**下一步**：等待所有 Workflow 完成 → 最终验收 → ADB 截图

## 执行遥测

| 任务 | 实际工作量 | S.U.P.E.R 评分 | 备注 |
|------|-----------|---------------|------|
| Phase 1 分析 | - | - | 15 agents 并行，~12min |
| 全量实现 | - | - | 7 commits, 118 files, +27,194/-1,705 lines |
| 文档产出 | - | - | 28 个 .md 文件（PRD/架构/设计/API/安全/测试/合规） |
| 测试产出 | - | - | 14 个测试文件 + 6 个 Fake 实现 |
| UI 组件 | - | - | 21 个 Composable |
