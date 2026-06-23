# RikkaAgent vs RikkaHub 综合技术分析报告

> Phase 1 分析产出 | 2026-06-23 | 15 agents 并行分析

---

## 1. 项目现状总结

### 1.1 RikkaAgent 优势

**模块化分层清晰**。五模块结构（`core:model` / `core:ssh` / `core:storage` / `core:ui` / `app`）职责边界明确，`core:model` 零 Android 依赖，`core:ssh` 接口定义与实现分离，`SshExecRunner` 接口允许替换 SSH 库而不触碰 UI 代码。

**接口抽象成熟**。`ProfileStore`、`ChatRepository`、`KnownHostsStore`、`SshExecRunnerFactory`、`KeyContentProvider`、`PasswordProvider`、`PassphraseProvider` 全部有接口定义，测试中使用手写 Fake 替换，无 Mockito/MockK 依赖。

**SSH 引擎设计合理**。sshj 封装层通过 `callbackFlow` 桥接同步 I/O 为 Kotlin Flow，JSONL 解析器 schema-tolerant 且测试覆盖良好（11 个测试用例）。PuTTY/OpenSSH 双格式密钥支持、Ed25519 密钥生成、EncryptedFile 加密存储构成完整密钥管理链路。

**Codex 集成架构可用**。`CommandComposer.wrapForCodex()` + `JsonlParser` + `CodexProgressFormatter` 形成完整管线。`StructuredEvent` 的 `kind` 字段是开放枚举，支持扩展到其他 AI CLI。

### 1.2 RikkaAgent 不足

**ChatViewModel 是 God Object**。529 行代码承担 SSH 连接管理、命令组装、输出格式化、JSONL 解析、HostKey/Password/Passphrase 回调中继、消息持久化、会话导出等全部职责。构造函数接收 8 个参数。

**数据模型是纯文本**。`ChatMessage(content: String)` 无法表达附件、工具调用、多模态内容、推理过程。`ChatThread` 无助手关联、无置顶、无分支。

**存储层无 Migration**。`AppDatabase` version=4 但无任何 Migration 定义，依赖 `fallbackToDestructiveMigration()`。`insertMessage` 使用 `OnConflictStrategy.REPLACE` 会整行覆盖，且每次插入新消息时 `dao.updateThread(threadId, title = "", ...)` 将 thread 标题清空——数据丢失 bug。

**SSH 引擎有 `runBlocking` 死锁风险**。`SshjExecRunner.buildVerifier()` 内部 6 处 `runBlocking` 调用，在 sshj transport 线程上阻塞等待 UI 回调。

**UI 层有功能性缺陷**。`looksLikeMarkdown()` 启发式判断导致纯文本被渲染为代码卡。Mermaid "渲染" 实际是 stub。Markdown 中的链接不可点击、图片被静默丢弃、无语法高亮。

**测试覆盖基础设施裸奔**。`SshjExecRunner`（核心执行器）零测试、`SshConnectionPool` 零测试、Room DAO 零测试。CI 无覆盖率门禁、无静态分析。

### 1.3 RikkaHub 核心竞争力

**完整的 AI 聊天应用架构**。多 Provider 支持（OpenAI/Google/Claude + 17 个内置 Provider 配置）、完整 Tool Calling 链路（6 种内置工具 + MCP 外部工具 + HITL 审批）、14 个搜索 Provider、6 个 TTS Provider、消息分支树（`MessageNode`）、多助手系统、Transformer 管线（Input 6 层 + Output 3 层）。

**UI 体系成熟度远超 RikkaAgent**。MaterialExpressiveTheme + 5 预设主题 + 动态取色 + AMOLED + 50 语义扩展色。`ChatMessage` 组件族（7 个子文件）支持 reasoning 折叠/预览/展开三态、tool call 审批流、分支选择器、翻译折叠、收藏、WebView 预览。`ChatInput` 支持模型选择、搜索开关、reasoning budget、MCP picker、文件附件、全屏编辑。Markdown 渲染使用 IntelliJ `MarkdownParser` AST 后台线程解析 + 语法高亮 + LaTeX + 表格 + 可缩放图片。

**数据层设计经受了 17 次 Migration 考验**。从 v1 到 v17 的完整迁移历史。`ConversationRepository` 使用 `database.withTransaction {}` 保证原子性。

**MCP 集成是生产级的**。自研 `SseClientTransport` 和 `StreamableHttpClientTransport`，支持配置热更新、指数退避自动重连。

---

## 2. 关键发现 Top 5

### 发现 1：消息模型是重构的阻塞点

`ChatMessage(content: String)` 无法表达 RikkaHub 的 `UIMessagePart`（Text/Image/Video/Audio/Document/Tool/Reasoning 七种类型）。无法支持 Tool Calling、推理过程展示、多模态内容、消息分支。

### 发现 2：ChatViewModel 是 God Object

8 个 `MutableStateFlow`/`MutableSharedFlow` 在同一个 ViewModel 中管理。`_messages.update { ... }` 从多个协程分支并发更新。Codex 渲染逻辑（170 行）和 Auth 回调中继应抽取为独立协作者。

### 发现 3：Transformer 管线是可移植的架构模式

RikkaHub 的 Input/Output Transformer 管线是纯函数式 `fold` 链，可直接应用到 RikkaAgent。当前 `CommandComposer`、`OutputFormatter`、`CodexProgressFormatter`、`AnsiStripper` 可封装为 Transformer。

### 发现 4：存储层存在数据丢失 bug 和安全隐患

- Bug 1: `insertMessage` 每次将 thread 标题覆写为空字符串
- Bug 2: `REPLACE` 策略导致流式重连时消息丢失
- 安全隐患: `codexApiKey` 明文存储
- 命令注入风险: `wrapForCodex()` 未转义 `$`、`` ` ``、`\`

### 发现 5：RikkaHub fork 停留在 2.1.2，上游已到 2.3.2

上游新增：免密钥网页搜索、语音识别、平板旋转修复、全面屏适配。

---

## 3. 重构可行性评估

| 任务 | 价值 | 难度 | 工作量 | 风险 |
|------|------|------|--------|------|
| 数据模型升级 | P0 阻塞一切 | 高 | 5-8 人天 | 高 |
| ChatViewModel 拆分 | P0 可测试性 | 中 | 3-5 人天 | 中 |
| UI 对齐 RikkaHub | P1 体验飞跃 | 高 | 15-25 人天 | 中高 |
| SSH runBlocking 消除 | P1 死锁风险 | 中 | 2-3 人天 | 中 |
| 存储层修复 | P0 数据安全 | 低 | 1-2 人天 | 低 |
| 测试补全 | P1 防回归 | 中 | 5-8 人天 | 低 |

---

## 4. 重构方向建议

### UI 对齐策略

**不要直接复制 RikkaHub 组件**——深度耦合 `UIMessage`/`MessageNode`，适配成本高于重写。分三步：

1. **替换 Markdown 渲染器**（1-2 天）：IntelliJ MarkdownParser + HighlightCodeBlock + ZoomableAsyncImage
2. **引入 MessagePart 模型 + ChainOfThought**（3-5 天）：拆分 ChatBubble，引入 Reasoning 三态
3. **升级输入组件 + 侧边栏**（5-8 天）：ChatInputState、助手选择、Paging 分页

### 架构改进策略

Phase 1: 修 Bug + 安全 → Phase 2: ChatViewModel 拆分 → Phase 3: 数据模型升级 → Phase 4: AgentRunner 抽象 → Phase 5: runBlocking 消除 → Phase 6: 连接池整合

### 模块复用策略

**可直接复用**：ChainOfThought、Tag/TagList、FormItem、DotLoading、ErrorCard、ExtendColors

**需适配后复用**：MarkdownBlock、HighlightCodeBlock、ChatMessageReasoningStep、DataTable、ZoomableAsyncImage

**不建议复用**：ChatMessage/ChatMessageCot（深度耦合 UIMessage）、ChatInput（耦合 MCP/搜索）、ChatDrawerContent（依赖 Paging 3/助手系统）

---

## 5. 开放问题

1. **定位**：保持 SSH 客户端 vs 演化为远程 AI Agent 平台 vs 与 RikkaHub 合并
2. **Fork 同步**：是否直接切换到上游 2.3.2
3. **数据迁移回滚**：是否保留 `content` 列作为 deprecated 字段
4. **runBlocking 优先级**：是否作为 P0 修复
5. **测试策略**：嵌入式 SSH 服务器 vs Mock
6. **静态分析**：是否引入 detekt/ktlint
