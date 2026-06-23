# RikkaAgent 重构 — 项目概览

> 自动生成于 Phase 1 分析（2026-06-23）

## 项目定位

RikkaAgent 是一个 Android SSH 命令执行器，以聊天式 UI 展示命令输出。核心场景：通过 SSH 远程执行 Codex/Claude Code/Gemini CLI 等 AI 工具。

## 目标

1:1 复刻 RikkaHub 的 UI/前端层，适配 SSH 命令执行场景。UI 完全一致。

## 技术栈

| 维度 | RikkaAgent | RikkaHub |
|------|-----------|----------|
| 语言 | Kotlin 2.1.0 | Kotlin |
| UI | Jetpack Compose | Jetpack Compose |
| DI | Koin | Koin |
| DB | Room v4 | Room v17 |
| 网络 | sshj 0.39.0 | OkHttp + SSE |
| Markdown | commonmark-java | IntelliJ MarkdownParser |
| 主题 | 自定义三模式 | MaterialExpressiveTheme + 5 预设 |

## 模块结构

```
rikka-agent/
├── :app              → Screens, Navigation, ViewModels, DI
├── :core:model       → Domain models (SshProfile, ChatMessage)
├── :core:ssh         → SSH runner, JSONL parser, host key store
├── :core:storage     → Room + DataStore persistence
└── :core:ui          → Reusable Compose components
```

## 关键发现

1. **数据模型是阻塞点** — `ChatMessage(content: String)` 无法表达 RikkaHub 的 `UIMessagePart`（7 种类型）
2. **ChatViewModel 是 God Object** — 529 行，8 个 StateFlow，构造函数 8 参数
3. **存储层有 bug** — `insertMessage` 用 `REPLACE` 会清空 thread 标题
4. **SSH 引擎有死锁风险** — `buildVerifier()` 内 6 处 `runBlocking`
5. **UI 层功能性缺陷** — Markdown 链接不可点击、Mermaid 是 stub、无语法高亮

## 详细分析

- [综合技术分析报告](analysis-report.md)
