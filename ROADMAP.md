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
- [x] spec TODO 集中索引（`docs/spec/99-spec-hygiene.md`）
- [x] 实现-规范偏差矩阵（`docs/spec/72-implementation-gap-matrix.md`）
- [ ] 规范冻结检查：spec 用词统一 + TODO 集中

## M2 待完成

- [x] Markdown 渲染 v1（段落/标题/列表/引用/链接/行内代码/代码块/表格/删除线）
- [x] CodeCard 基础组件（折叠/展开/复制/语言标签）
- [x] 流式渲染策略（流式阶段用 CodeCard，终态切 MarkdownText 单次解析 + remember 缓存）
- [x] Mermaid 渲染设计草案（`docs/spec/23-mermaid.md`）
- [x] Mermaid 技术选型对比（`docs/spec/24-mermaid-options.md`）
- [x] 可选：Mermaid 渲染（WebView/JS bridge）

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
- [x] CI 执行摘要（step summary）
- [x] Spec 测试映射与发布验收矩阵文档
- [x] 命令包装测试（`CommandComposerTest`：shell/codex/workdir/env）
- [x] 会话导出格式测试（`SessionExporterTest`）
- [x] SSH 错误映射测试（`ErrorMessageMapperTest`）
- [x] KnownHosts 存储测试（`InMemoryKnownHostsStoreTest` + `KnownHostsEntryCodecTest`）
- [x] Storage 映射测试（`MappersTest`：entity/model roundtrip + 非法枚举）
- [x] HostKey mismatch 二次确认（替换信任前二次确认）
- [x] README 首页信息架构重构（矩阵化 + 快速验证路径）
- [x] 命令/输出/JSONL 边界测试增强（`CommandComposerTest`/`OutputFormatterTest`/`JsonlParserTest`）
- [x] Mermaid 功能开关落地（设置页 + DataStore 偏好，默认关闭）
- [x] Mermaid 基础渲染链路落地（Fence 检测/分段模型/WebView卡片/失败降级/重试）
- [x] Mermaid 主题映射与模板单测（`MermaidRenderSupportTest`）
- [x] CI 测试矩阵增强（core/app 模块化测试 + 失败诊断摘要）
- [x] README 展示增强（UI Gallery + Quick Demo + FAQ）
- [x] 取消态语义落地（`MessageStatus.Canceled` + ChatViewModel cancel 持久化）
- [x] i18n 键一致性审计（app/core-ui 中英资源零缺失）
- [x] 取消态规则单测（`CancelMessageHelperTest`）
- [x] JsonlParser 复杂嵌套测试增强（nested content / 字段优先级）

---

## 下一步最优动作

1. **补 ProfileEditor 测试闭环** — 覆盖保存映射、字段校验、认证切换边界
2. **加强 Codex JSONL 事件可视化** — 将 `thread/turn/item` 事件映射为结构化 UI 进度
3. **补 Key import 集成测试** — 校验 `ContentUriKeyContentProvider` 与导入链路

---

## Agent 交接快照（2026-03-13）

### 当前已完成到哪里

1. 取消态语义已落地并可持久化：`MessageStatus.Canceled` + `cancelRunning()` 回写消息。
2. Mermaid 已具备“开关 + 分段 + 卡片 + 本地离线渲染路径（无外网依赖）”。
3. README 已完成发布导向重构（矩阵/FAQ/Gallery/Quick Demo）。
4. i18n 键一致性审计已完成（app/core-ui 中英 0 缺失），见 `docs/i18n-key-audit.md`。

### 正在进行中的主线

1. Key import / KeyContentProvider 集成测试。
2. Codex JSONL 进度事件可视化。
3. 发布门禁回归验证（模块单测 / lint / assemble）。

### 下一位 Agent 建议直接执行顺序

1. 先补 Key import 与存储/集成测试（KnownHosts/DataStore/KeyContentProvider）。
2. 再补 Codex 进度事件的展示与测试。
3. 最后执行全回归与发布门禁（模块单测 -> lint -> assemble）。

### 本机验证前置（已验证可用）

PowerShell 先设 JDK：

```powershell
$env:JAVA_HOME='d:\Code\Projects\rikka-agent\tmp\jdk17\jdk-17.0.18+8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

推荐验证命令：

```powershell
.\gradlew :core:model:testDebugUnitTest :core:ssh:testDebugUnitTest :core:ui:testDebugUnitTest :core:storage:testDebugUnitTest :app:testDevDebugUnitTest --no-daemon
.\gradlew :app:lintDevDebug :app:assembleDevDebug --no-daemon
```
