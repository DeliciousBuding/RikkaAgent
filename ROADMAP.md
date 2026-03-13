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
- [x] 规范冻结检查：spec 用词统一 + TODO 集中

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
- [x] Codex `thread/turn/item` 进度摘要（ChatViewModel 结构化展示 + 回归测试）
- [x] `ChatScreen` 分享/导出 chooser payload 测试（普通输出 / 完整输出 / 会话导出）

---

## 下一步最优动作

1. **执行发布门禁回归** — 模块单测 + lint + assemble 持续收口

---

## 2026-03-14 冲刺 TODO（已完成）

- [x] 复跑 `:app:connectedDevDebugAndroidTest` 并确认本地通过
- [x] 移除 androidTest 中的旧 Kotlin provider 文件
- [x] 使用 Java ContentProvider 实现测试 Documents provider
- [x] 在 androidTest Manifest 注册测试 provider
- [x] SAF 选择器回归测试固定到稳定 UI 控件
- [x] SAF 选择器回归测试断言非持久化权限提示
- [x] ChatScreen 分享/导出分发回归测试补齐
- [x] Codex 进度 UI 渲染回归测试补齐
- [x] androidTest 依赖补齐（core/runner/espresso intents）
- [x] 新增 instrumentation 测试操作文档
- [x] 文档写明 SAF provider authority 与类名
- [x] 文档补充 connected 测试报告路径
- [x] 文档补充模拟器启动与 boot 校验步骤
- [x] 文档补充 `JAVA_HOME` 诊断提示
- [x] README 增加 instrumentation 文档入口
- [x] README 增加 Windows `JAVA_HOME` 示例
- [x] Testing spec 增加 instrumentation 测试范围说明
- [x] Test mapping 增加 TestDocumentsProvider 覆盖项
- [x] CI 强制 Node24 JS actions 兼容开关
- [x] CI Summary 统计测试/失败/跳过计数
- [x] CI 持续上传 APK 与 lint/test 报告
- [x] SAF 选择器 Intent 注入 ClipData + read flags
- [x] 测试 provider 以稳定 authority 暴露 content URI
- [x] 测试 provider 返回可读取的临时密钥内容
- [x] connected 测试锁定 devDebug 变体
- [x] Docs Hub 增加 instrumentation 测试入口
- [x] ARCHIVE 记录 2026-03-14 测试/文档加固
- [x] SSH/Auth 覆盖清单包含 SAF instrumentation 测试
- [x] README 保留 connected 测试命令入口

## 2026-03-14 术语与测试加固（已完成）

- [x] 统一 spec 中 `known hosts` / `complete output` / `Mode A` 术语
- [x] 更新 gap matrix：M1 冻结与 README 发布质量标记完成
- [x] JSONL parser 增补分块 + trailing 文本用例
- [x] 完整输出相关 UI 文案对齐为 "complete output"
- [x] 新增统一验证清单文档
- [x] microcopy 与组件清单补充 complete output 对话框
- [x] 输出截断提示文案与 microcopy 对齐
- [x] spec hygiene 增补 complete output 标题/动作规范
- [x] testing spec 增补 verification 文档指引
- [x] PR 模板与贡献指南对齐 verification 清单
- [x] 发布/验收文档补充 verification 与 instrumentation 指引
- [x] 架构文档对齐实际实现（SshConnectionPool / RunnerFactory）
- [x] 架构 spec 补齐 RunnerFactory/ConnectionPool 接口说明
- [x] 架构文档补充 SshjExecRunner 复用说明
- [x] 新增 troubleshooting 文档（JDK/SDK/Emulator/Lint/APK）
- [x] Spec index 补充 supporting docs 入口
- [x] SECURITY/STATE/ENV 文档指向 troubleshooting 与 verification
- [x] 新增 glossary 术语表并链接到 spec index 与 README

## Agent 交接快照（2026-03-13）

### 当前已完成到哪里

1. 取消态语义已落地并可持久化：`MessageStatus.Canceled` + `cancelRunning()` 回写消息。
2. Mermaid 已具备“开关 + 分段 + 卡片 + 本地离线渲染路径（无外网依赖）”。
3. README 已完成发布导向重构（矩阵/FAQ/Gallery/Quick Demo）。
4. i18n 键一致性审计已完成（app/core-ui 中英 0 缺失），见 `docs/i18n-key-audit.md`。
5. Codex `thread/turn/item` 事件已映射为结构化进度摘要，并有 ViewModel/解析层测试兜底。
6. 截断输出的 CodeCard 展开、完整输出动作行、用户重跑按钮已有 Compose 回归覆盖。
7. 完整输出弹窗的展示、分享回调、关闭动作已有 screen 级 Compose 回归覆盖。
8. `ChatScreen` 分享/导出 chooser payload 已有单测覆盖（普通输出 / 完整输出 / 会话导出）。
9. `ChatScreen` 端到端分享/导出分发（`startActivity`）回归覆盖已补齐。
10. SAF 端到端选择器验证（ActivityResult + persistable permission）已补齐。
11. Codex 进度 UI 渲染回归测试已补齐。

### 正在进行中的主线

1. 发布门禁回归验证（模块单测 / lint / assemble）。

### 下一位 Agent 建议直接执行顺序

1. 直接执行全回归与发布门禁（模块单测 -> lint -> assemble）。

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
