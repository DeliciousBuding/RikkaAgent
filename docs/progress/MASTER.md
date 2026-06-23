# MASTER.md — RikkaAgent 重构进度追踪

> 最后更新：2026-06-23
> 追踪模式：LOCAL_ONLY
> 里程碑：v0.2.0-refactor-milestone ✅

## 任务定义

**目标**：1:1 复刻 RikkaHub UI/前端层，适配 SSH 命令执行场景，UI 完全一致。

**范围**：RikkaAgent 全仓库重构（UI + 数据模型 + 存储 + SSH 引擎 + 安全加固 + 工程现代化）

**完成状态**：✅ 重构完成

---

## 最终统计

| 维度 | 数值 |
|------|------|
| Commits | 10 |
| Files changed | 128 |
| Insertions | +30,270 |
| Deletions | -1,843 |
| 生产代码 | 19,710 lines |
| 测试代码 | 7,338 lines |
| 文档 | 28 个 .md 文件 |
| UI 组件 | 22 个 Composable |
| ViewModel | 13 个 |
| 测试文件 | 43 个（含 6 Fake） |

---

## 阶段总览

| 阶段 | 状态 | 说明 |
|------|------|------|
| Phase 0: 同步与基线 | ✅ 完成 | RikkaHub fork 同步到 v2.3.2 |
| Phase 1: 数据层重构 | ✅ 完成 | MessagePart + Room Migration + Bug 修复 + VM 拆分 |
| Phase 2: UI 基础设施 | ✅ 完成 | 主题系统 + 基础组件库 + 图标库 + Navigation |
| Phase 3: 核心 UI 复刻 | ✅ 完成 | Markdown + 代码高亮 + 气泡 + 输入框 + ChatScreen |
| Phase 4: SSH 适配 | ✅ 完成 | SshOutputMapper + 死锁修复 + Codex JSONL + 连接状态 |
| Phase 5: 安全与展示 | ✅ 完成 | 安全加固 + 文档 + CI/CD + 合规 |

---

## 架构产出

```
:app           → Screens, Navigation, ViewModels, DI
:core:model    → MessagePart, ChatModels, SshProfile
:core:ssh      → SshjExecRunner, SshOutputMapper, JsonlParser
:core:storage  → Room v5, DataStore, Repository
:core:ui       → Theme, Components (22 composables)
```

---

## 文档索引

| 类别 | 文档 |
|------|------|
| 分析 | [project-overview.md](../analysis/project-overview.md) — [analysis-report.md](../analysis/analysis-report.md) |
| 规划 | [task-breakdown.md](../plan/task-breakdown.md) |
| 产品 | [prd.md](../prd.md) — [design.md](../design.md) |
| 架构 | [architecture.md](../architecture.md) — [design-use-cases.md](../design-use-cases.md) |
| API | [api.md](../api.md) |
| 安全 | [security.md](../security.md) — [threat-model.md](../threat-model.md) — [privacy-audit.md](../privacy-audit.md) |
| 测试 | [testing.md](../testing.md) — [testing-conventions.md](../testing-conventions.md) |
| 合规 | [license-compliance.md](../license-compliance.md) — [privacy-compliance.md](../privacy-compliance.md) — [product-review.md](../product-review.md) |
| 未来 | [design-agent-runner.md](../design-agent-runner.md) — [design-pty.md](../design-pty.md) |
| 工程 | [CHANGELOG.md](../CHANGELOG.md) — [dependency-audit.md](../dependency-audit.md) |

---

## 执行遥测

| 任务 | Workflows | Agents | Duration |
|------|-----------|--------|----------|
| Phase 1 分析 | 1 | 15 | ~12min |
| Phase 1-5 实现 | 6 | 50+ | ~30min |
| 验收审查 | 3 | 20+ | ~15min |
| Spec 文档 | 2 | 8 | ~10min |
| 测试+构建 | 2 | 10 | ~20min |
| 合规+卫生 | 1 | 5 | ~10min |
| **总计** | **12+** | **100+** | **~90min** |
