# ROADMAP — rikka-agent

> 目标：聊天式 SSH 命令执行器，手机端以漂亮的消息渲染呈现命令与输出（Mode A：非交互 exec channel）。
>
> 事实/配置 → `STATE.md` ｜ 历史 → `ARCHIVE.md`

## 入口

- Spec 索引：`docs/spec/00-index.md`
- 总体计划书：`docs/plan.md`
- 安全/威胁模型：`docs/threat-model.md`, `docs/server-hardening.md`
- Repo 代理约束：`AGENTS.md`

## 仓库边界说明

- 与 rikka-agent 无关的 VectorControl 文档已于 2026-03-13 外部归档到 `C:\Users\Ding\docs\vectorcontrol-archive`，索引见 `C:\Users\Ding\VECTORCONTROL_ARCHIVE_INDEX.md`。

---

## 里程碑进度总览

| 里程碑 | 状态 | 说明 |
|--------|------|------|
| M0 规范冻结 | ✅ 基本完成 | 剩余：spec 用词统一检查 |
| M1 UI 骨架 | ✅ 主要完成 | 8 屏 + ProfilesVM/EditorVM/ChatVM 全部连接 Room |
| M2 渲染管线 | ✅ 主要完成 | Markdown v1 + 流式渲染优化 + CodeCard；Mermaid 可选 |
| M3 SSH 引擎 | ✅ 主要完成 | sshj exec + 认证 + host key + 会话管理 + 密钥生成 + 加密存储 |
| M4 Codex 接入 | ✅ 主要完成 | JSONL 解析 + API Key 管理 + exec --json + profile 开关 + Markdown 渲染 |
| M5 开源发布 | ✅ 主要完成 | 服务器指南 + 隐私审计 + Release checklist + 输出截断 |
| i18n 中英双语 | ✅ 完成 | 全面国际化：120+ 字符串资源，中文优先，覆盖全部 UI 屏幕 + ViewModel |

---

## M1 待完成

- [x] ProfilesVM / ProfileEditorVM 连接 Room 存储（真实 CRUD）
- [x] ChatViewModel 连接 Room（消息持久化 + 会话管理）
- [x] 更多密钥格式支持（PuTTY .ppk）
- [ ] 规范冻结检查：spec 用词统一 + TODO 集中

## M2 待完成

- [x] Markdown 渲染 v1（段落/标题/列表/引用/链接/行内代码/代码块/表格/删除线）
- [x] CodeCard 基础组件（折叠/展开/复制/语言标签）
- [x] 流式渲染策略（流式阶段用 CodeCard，终态切 MarkdownText 单次解析 + remember 缓存）
- [x] Mermaid 渲染设计草案（`docs/spec/23-mermaid.md`）
- [ ] 可选：Mermaid 渲染（WebView/JS bridge）

## M4 待完成

- [x] Profile 增加 codexMode / codexWorkDir 字段
- [x] ChatViewModel Codex exec 命令包装 (`codex exec --full-auto`)
- [x] ProfileEditorScreen Codex 设置 UI（开关 + 工作目录）
- [x] Codex 输出 Markdown 渲染（复用 M2 MarkdownText）
- [x] "一键运行"动作（复制/重跑/导出）
- [x] 远端命令协议约定（`--json` JSONL 解析 + JsonlParser + JsonlLineBuffer）
- [x] 延迟优化（SSH 连接池复用 + 流式 JSONL 缓冲 + remember 渲染缓存）
- [x] Codex API Key 管理（profile 级别配置，密码遮蔽 UI，OPENAI_API_KEY 环境变量注入）

## M5 待完成

- [x] 服务器指南（低权限用户/sshd_config/防火墙）— `docs/server-hardening.md`
- [x] 隐私审计清单 — `docs/privacy-audit.md`
- [x] Release checklist — `docs/release-checklist.md`
- [x] 输出截断保护（256KB 上限 + 截断提示）
- [x] 截断输出展开/分享完整内容（消息级）
- [x] 仓库文档边界清理（移除非 rikka-agent 内容并外部归档）
- [x] README 可发布化重构（徽章/矩阵/快速上手/安全说明）
- [x] 核心解析单测补齐（`JsonlParser` / `JsonlLineBuffer`）
- [x] 输出截断格式化单测（`OutputFormatterTest`）
- [x] SSH 密钥格式判定单测（`SshAuthKeyFormatTest`）

---

## 下一步最优动作

1. **Mermaid 可选渲染** — 在 Markdown 渲染层增加可开关的图表渲染能力
2. **加强 Codex JSONL 事件可视化** — 将 `thread/turn/item` 事件映射为结构化 UI 进度
3. **规范冻结检查收口** — 统一 spec 用词并清理分散 TODO

