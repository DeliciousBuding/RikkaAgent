# RikkaAgent 重构 — 任务分解

> 基于 Phase 1 分析 | 2026-06-23

## 总览

- **总工期**: ~16 周，关键路径 ~61 天
- **验收方式**: ADB (Mumu 模拟器) + Sonnet 模型截图验收
- **阶段**: 6 个 Phase，~25 个 Task

## 关键路径

`0A → 0B → 0C → 1A → 1B → 1D → 4A → 4C → 5C`（~61 天）

## 并行通道

- **Path A (UI)**: `0C → 2A → 2B → 3A → 3B/3C/3D/3E → 3F → 3H`
- **Path B (数据+SSH)**: `1A → 1B → 1C → 1D → 4A → 4B/4C/4D`
- **Path C (独立组件)**: `3B / 3C / 3D / 3E` 可并行

---

## Phase 0: 同步与基线建立

| Task | 优先级 | 工作量 | 描述 | 验收标准 |
|------|--------|--------|------|----------|
| 0A — Fork 上游 Rebase | P0 | XL | 将 RikkaHub fork 与上游 v2.3.2 合并 | assembleDebug 零错误 + 单测通过 |
| 0B — 冲突解决 + 编译验证 | P0 | M | 收尾冲突解决 | lintDebug 零 error |
| 0C — SSH Smoke Test | P0 | S | 验证 SSH exec 通路未被破坏 | SSH 连接成功 + 命令输出正确 |

**ADB 检查点**: 主页/设置页/聊天页 + SSH smoke test（~4 张截图）

---

## Phase 1: 数据模型与存储层重构

| Task | 优先级 | 工作量 | 描述 | 验收标准 |
|------|--------|--------|------|----------|
| 1A — MessagePart Sealed Class | P0 | L | ChatMessage(content: String) → sealed class 体系 | 序列化 roundtrip + 向后兼容 |
| 1B — Room Migration v3→v4 | P0 | M | 升级 schema 支持 MessagePart | 旧数据自动迁移不丢失 |
| 1C — 存储层 Bug 修复 | P0 | M | 修复 insertMessage REPLACE + codexApiKey 明文 + 索引 | 回归测试通过 |
| 1D — ChatViewModel 拆分 | P1 | XL | 拆为 SessionManager/CommandExecutor/AuthCallbackBroker | 代码量减少 60%+ |

**ADB 检查点**: 旧聊天记录保留 + API Key 密文 + 会话标题正常 + SSH 执行流程（~5 张截图）

---

## Phase 2: UI 基础设施对齐

| Task | 优先级 | 工作量 | 描述 | 验收标准 |
|------|--------|--------|------|----------|
| 2A — 主题系统对齐 | P0 | M | 移植 ExtendedColors + Material You 动态取色 | 亮/暗/AMOLED 三模式一致 |
| 2B — 基础组件库移植 | P0 | L | ChainOfThought/Tag/FormItem/DotLoading/ErrorCard 等 | Preview 渲染一致 |
| 2C — 图标库统一 | P1 | S | Lucide Icons 统一 | 视觉一致 |
| 2D — Navigation + UiState | P1 | M | NavHost + UiState 四态模板 | 路由跳转无 crash |

**ADB 检查点**: 主题三模式 + 组件 Preview + 图标对比 + 路由跳转（~12 张截图）

---

## Phase 3: 核心 UI 复刻

| Task | 优先级 | 工作量 | 描述 | 验收标准 |
|------|--------|--------|------|----------|
| 3A — MarkdownBlock | P0 | L | 移植 RikkaHub Markdown 渲染器 | 全元素渲染一致 + 链接可点击 |
| 3B — HighlightCodeBlock | P0 | L | 语法高亮 + 代码折叠 | 50+ 语言 + 折叠正常 |
| 3C — ReasoningStep | P1 | M | 推理步骤折叠/展开 | 三态切换正常 |
| 3D — DataTable | P1 | M | 表格组件 | 水平滚动 + 列宽自适应 |
| 3E — Mermaid | P1 | L | 升级 Mermaid 渲染 | 流程图/时序图正确 |
| 3F — ChatMessage 气泡 | P0 | L | 重写适配 MessagePart 的气泡 | 像素级一致 |
| 3G — ChatInput | P0 | M | 重写 SSH 场景输入框 | 视觉一致 + 回车发送 |
| 3H — ChatScreen 整合 | P0 | L | 整合所有组件 | 完整聊天流程可运行 |

**ADB 检查点**: Markdown/代码高亮/推理/表格/Mermaid/气泡全态/输入框全态/ChatScreen 全流程（~20 张截图）

---

## Phase 4: SSH 适配层与集成

| Task | 优先级 | 工作量 | 描述 | 验收标准 |
|------|--------|--------|------|----------|
| 4A — SshOutputMapper | P0 | L | stdout/stderr → MessagePart 桥接 | 长输出不卡顿 + ANSI 正确清理 |
| 4B — runBlocking 死锁修复 | P0 | M | 消除 SSH 引擎死锁风险 | 100 次循环无死锁 |
| 4C — Codex JSONL 集成 | P1 | L | JSONL → MessagePart 映射 | 推理步骤实时显示 |
| 4D — 连接状态 UI | P1 | M | 对齐 RikkaHub 连接状态 UI | 每种错误状态有对应 UI |

**ADB 检查点**: SSH 输出渲染/Codex 推理/错误状态/连接状态（~10 张截图）

---

## Phase 5: 安全加固与对外展示

| Task | 优先级 | 工作量 | 描述 | 验收标准 |
|------|--------|--------|------|----------|
| 5A — 敏感信息清理 | P0 | M | 代码扫描 + 日志脱敏 | grep 无敏感信息 |
| 5B — 产品文档 | P1 | S | README/STATE.md 更新 | 无误导 |
| 5C — 全量截图验收 | P0 | M | ADB 全量截图 + Sonnet 对比 | 视觉差异 < 5% |
| 5D — CI/CD 更新 | P2 | S | GitHub Actions 更新 | CI 全绿 |

**ADB 检查点**: 全量页面截图矩阵（~15+ 张截图）
